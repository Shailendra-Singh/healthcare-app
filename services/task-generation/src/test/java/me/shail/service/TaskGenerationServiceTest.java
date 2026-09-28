package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import io.quarkus.arc.ClientProxy;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import me.shail.client.CareNeed;
import me.shail.client.EvaluationRun;
import me.shail.client.RulesEngineClient;
import me.shail.dto.TaskDto;
import me.shail.dto.TaskUpdateRequest;
import me.shail.model.GenerationRun;
import me.shail.model.Task;
import me.shail.support.Needs;
import me.shail.support.TestDatabase;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reconciliation against a real task_generation database, over consecutive rules-engine evaluations (mocked).
 */
@QuarkusTest
class TaskGenerationServiceTest {

    static final LocalDate DAY1 = LocalDate.of(2026, 9, 28);

    @Inject
    TaskGenerationService taskGenerationService;

    @Inject
    TaskService taskService;

    @Inject
    TestDatabase db;

    @InjectMock
    @RestClient
    RulesEngineClient rulesEngine;

    String alice;
    String bob;

    @AfterEach
    void restorePageSize() {
        // The service bean is shared by every test class in this Quarkus run
        ClientProxy.unwrap(taskGenerationService).pageSize = 500;
    }

    @BeforeEach
    void setUp() {
        db.reset();
        ClientProxy.unwrap(taskGenerationService).pageSize = 3;
        alice = Needs.patientId();
        bob = Needs.patientId();
    }

    /** Makes {@code runId} the latest successful evaluation, with these care needs (paged by 3, as the API does). */
    void evaluation(long runId, CareNeed... needs) {
        when(rulesEngine.latestSucceeded("SUCCEEDED")).thenReturn(new EvaluationRun(runId, DAY1, "SUCCEEDED"));
        List<CareNeed> all = Arrays.asList(needs);
        for (int page = 1; page <= all.size() / 3 + 1; page++) {
            List<CareNeed> slice = all.subList(Math.min((page - 1) * 3, all.size()), Math.min(page * 3, all.size()));
            when(rulesEngine.careNeeds(runId, page, 3)).thenReturn(new ArrayList<>(slice));
        }
    }

    GenerationRun reconcile() {
        return taskGenerationService.run(GenerationRun.Trigger.MANUAL).orElseThrow();
    }

    List<TaskDto> tasks(String status) {
        List<Task.Status> statuses = status == null ? List.of(Task.Status.values()) : List.of(Task.Status.valueOf(status));
        return taskService.search(statuses, null, null, null, null, 1, 1000);
    }

    @Test
    void dueNeedsBecomeSchedulingOrReferralTasksByProgramPolicy() {
        evaluation(1,
                Needs.wellness(alice, 180, DAY1.minusDays(200), null, DAY1),                          // PCP past cadence
                Needs.wellness(bob, 365, null, null, DAY1),                                           // PCP never seen
                Needs.diabetes(alice, "Endocrinology", 90, DAY1.minusDays(100), null, DAY1),          // past cadence
                Needs.diabetes(alice, "Podiatry", 180, null, null, DAY1),                             // never seen
                Needs.diabetes(alice, "Cardiology", 90, DAY1.minusDays(10), null, DAY1),              // within cadence
                Needs.diabetes(alice, "Nephrology", 180, null, DAY1.plusDays(3), DAY1));              // booked

        GenerationRun run = reconcile();

        assertEquals(GenerationRun.Status.SUCCEEDED, run.status, run.errorMessage);
        assertEquals(6, run.needsRead);
        assertEquals(3, run.tasksCreated);
        Map<String, String> byNeed = tasks("OPEN").stream()
                .collect(Collectors.toMap(t -> t.sourcePatientId().equals(alice) ? "alice/" + t.specialty() : "bob/" + t.specialty(),
                        TaskDto::taskType));
        assertEquals(Map.of("alice/PCP", "SCHEDULING", "alice/Endocrinology", "SCHEDULING", "alice/Podiatry", "REFERRAL"), byNeed);

        TaskDto endo = tasks("OPEN").stream().filter(t -> t.specialty().equals("Endocrinology")).findFirst().orElseThrow();
        assertEquals(90, endo.cadenceDays());
        assertEquals(DAY1.minusDays(10), endo.dueDate(), "last visit + cadence");
        var history = taskService.find(endo.taskId()).orElseThrow().events();
        assertEquals(1, history.size());
        assertEquals("SYSTEM", history.getFirst().actor());
        assertEquals("created from evaluation run 1: last visit " + DAY1.minusDays(100) + " is older than the 90-day cadence",
                history.getFirst().reason());
    }

    @Test
    void reconcilingTheSameEvaluationAgainChangesNothing() {
        CareNeed[] needs = {
                Needs.wellness(alice, 180, DAY1.minusDays(200), null, DAY1),
                Needs.diabetes(alice, "Podiatry", 180, null, null, DAY1)};
        evaluation(1, needs);
        reconcile();

        GenerationRun again = reconcile();

        assertEquals(0, again.tasksCreated);
        assertEquals(0, again.tasksUpdated);
        assertEquals(0, again.tasksClosed);
        assertEquals(2, tasks(null).size());
    }

    @Test
    void theNextEvaluationClosesTasksWithTheReason() {
        evaluation(1,
                Needs.diabetes(alice, "Endocrinology", 90, DAY1.minusDays(100), null, DAY1),
                Needs.diabetes(alice, "Cardiology", 90, DAY1.minusDays(100), null, DAY1),
                Needs.diabetes(alice, "Podiatry", 180, null, null, DAY1),
                Needs.wellness(bob, 180, DAY1.minusDays(300), null, DAY1));
        reconcile();

        evaluation(2,
                Needs.diabetes(alice, "Endocrinology", 90, DAY1.minusDays(1), null, DAY1),           // visited
                Needs.diabetes(alice, "Cardiology", 90, DAY1.minusDays(100), DAY1.plusDays(7), DAY1),  // booked
                Needs.diabetes(alice, "Podiatry", 180, DAY1.minusDays(400), null, DAY1));             // first visit long ago
        // bob: no longer in the evaluation
        GenerationRun run = reconcile();

        assertEquals(1, run.tasksCreated, "podiatry is now a scheduling task");
        assertEquals(4, run.tasksClosed);
        Map<String, String> reasons = tasks("RESOLVED").stream()
                .collect(Collectors.toMap(t -> t.specialty() + "/" + t.taskType(), TaskDto::closeReason));
        assertEquals("within cadence: last visit " + DAY1.minusDays(1) + ", next due " + DAY1.plusDays(89),
                reasons.get("Endocrinology/SCHEDULING"));
        assertEquals("appointment booked for " + DAY1.plusDays(7), reasons.get("Cardiology/SCHEDULING"));
        assertTrue(reasons.get("Podiatry/REFERRAL").startsWith("replaced by a SCHEDULING task"), reasons.get("Podiatry/REFERRAL"));
        assertEquals("no longer needed: evaluation run 2 has no PCP need for this patient in primary-care-wellness",
                reasons.get("PCP/SCHEDULING"));
        assertTrue(tasks("RESOLVED").stream().allMatch(t -> t.closedBy().equals("SYSTEM")));
        assertEquals(List.of("Podiatry/SCHEDULING"),
                tasks("OPEN").stream().map(t -> t.specialty() + "/" + t.taskType()).toList());
    }

    @Test
    void aTaskStillNeededIsUpdatedAndKeepsItsStatusAndAssignee() {
        CareNeed first = Needs.diabetes(alice, "Endocrinology", 90, DAY1.minusDays(100), null, DAY1);
        evaluation(1, first);
        reconcile();
        TaskDto task = tasks("OPEN").getFirst();
        taskService.update(task.taskId(), new TaskUpdateRequest("nurse.kim", Task.Status.IN_PROGRESS, "nurse.kim", null));

        // Moved to a stricter tier: shorter cadence, higher priority
        evaluation(2, new CareNeed(alice, "diabetes-management", "unmonitored", "Endocrinology", 30, DAY1.minusDays(100),
                DAY1.minusDays(70), null, "OVERDUE", "high", "Get labs done", Needs.SPECIALIST));
        GenerationRun run = reconcile();

        assertEquals(1, run.tasksUpdated);
        TaskDto updated = taskService.find(task.taskId()).orElseThrow();
        assertEquals(Task.Status.IN_PROGRESS, updated.status());
        assertEquals("nurse.kim", updated.assignee());
        assertEquals("unmonitored", updated.tierId());
        assertEquals(30, updated.cadenceDays());
        assertEquals("high", updated.priority());
        assertEquals(DAY1.minusDays(70), updated.dueDate());
    }

    @Test
    void aReferralKeepsTheDateItFirstBecameDue() {
        evaluation(1, Needs.diabetes(alice, "Podiatry", 180, null, null, DAY1));
        reconcile();

        LocalDate day2 = DAY1.plusDays(1);
        evaluation(2, Needs.diabetes(alice, "Podiatry", 180, null, null, day2));
        reconcile();

        assertEquals(DAY1, tasks("OPEN").getFirst().dueDate());
    }

    @Test
    void onePatientInTwoProgramsNeedingTheSameSpecialtyGetsOneTaskPerProgram() {
        evaluation(1,
                Needs.diabetes(alice, "Cardiology", 90, DAY1.minusDays(100), null, DAY1),
                Needs.need(alice, "senior-cardiology", "all", "Cardiology", 365, DAY1.minusDays(400), null, DAY1, Needs.SPECIALIST));

        reconcile();

        assertEquals(List.of("diabetes-management", "senior-cardiology"),
                tasks("OPEN").stream().map(TaskDto::programId).sorted().toList());
    }

    @Test
    void aPersonsDecisionStandsUntilTheLastVisitChanges() {
        evaluation(1, Needs.diabetes(alice, "Endocrinology", 90, DAY1.minusDays(100), null, DAY1),
                Needs.diabetes(alice, "Podiatry", 180, null, null, DAY1));
        reconcile();
        for (TaskDto task : tasks("OPEN")) {
            taskService.update(task.taskId(), new TaskUpdateRequest("dr.lee", Task.Status.CANCELLED, null, "patient declined"));
        }

        reconcile();
        assertTrue(tasks("OPEN").isEmpty(), "same gap: not recreated");

        evaluation(2, Needs.diabetes(alice, "Endocrinology", 90, DAY1.minusDays(95), null, DAY1),
                Needs.diabetes(alice, "Podiatry", 180, null, null, DAY1));
        GenerationRun run = reconcile();
        assertEquals(1, run.tasksCreated, "a new visit is a new gap");
        assertEquals("Endocrinology", tasks("OPEN").getFirst().specialty());
    }

    @Test
    void aScheduledCheckSkipsAnEvaluationAlreadyReconciled() {
        evaluation(1, Needs.wellness(alice, 180, DAY1.minusDays(200), null, DAY1));
        assertTrue(taskGenerationService.run(GenerationRun.Trigger.SCHEDULED).isPresent());

        assertTrue(taskGenerationService.run(GenerationRun.Trigger.SCHEDULED).isEmpty());

        evaluation(2, Needs.wellness(alice, 180, DAY1.minusDays(200), null, DAY1));
        assertTrue(taskGenerationService.run(GenerationRun.Trigger.SCHEDULED).isPresent());
    }

    @Test
    void nothingToDoBeforeTheFirstEvaluation() {
        when(rulesEngine.latestSucceeded("SUCCEEDED")).thenThrow(new WebApplicationException(404));

        assertTrue(taskGenerationService.run(GenerationRun.Trigger.SCHEDULED).isEmpty());
        assertTrue(taskService.latestRun().isEmpty());
    }

    @Test
    void aFailureIsRecordedAndLeavesTheTasksAsTheyWere() {
        evaluation(1, Needs.wellness(alice, 180, DAY1.minusDays(200), null, DAY1));
        reconcile();
        when(rulesEngine.careNeeds(anyLong(), anyInt(), anyInt())).thenThrow(new ProcessingException("Connection refused"));

        GenerationRun failed = reconcile();

        assertEquals(GenerationRun.Status.FAILED, failed.status);
        assertEquals("ProcessingException: Connection refused", failed.errorMessage);
        assertEquals(1, tasks("OPEN").size(), "no task is closed when the run could not read the needs");
        assertNull(failed.tasksClosed);
    }
}
