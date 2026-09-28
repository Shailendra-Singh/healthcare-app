package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import me.shail.client.EvaluationRun;
import me.shail.client.RulesEngineClient;
import me.shail.dto.TaskDto;
import me.shail.dto.TaskEventDto;
import me.shail.dto.TaskUpdateRequest;
import me.shail.model.GenerationRun;
import me.shail.model.Task;
import me.shail.support.Needs;
import me.shail.support.TestDatabase;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** People's changes to tasks, against a real database. */
@QuarkusTest
class TaskServiceTest {

    static final LocalDate DAY1 = LocalDate.of(2026, 9, 28);

    @Inject
    TaskService taskService;

    @Inject
    TaskGenerationService taskGenerationService;

    @Inject
    TestDatabase db;

    @InjectMock
    @RestClient
    RulesEngineClient rulesEngine;

    Long taskId;

    @BeforeEach
    void oneOpenTask() {
        db.reset();
        when(rulesEngine.latestSucceeded("SUCCEEDED")).thenReturn(new EvaluationRun(1L, DAY1, "SUCCEEDED"));
        when(rulesEngine.careNeeds(eq(1L), eq(1), anyInt())).thenReturn(List.of(
                Needs.diabetes(Needs.patientId(), "Endocrinology", 90, DAY1.minusDays(100), null, DAY1)));
        taskGenerationService.run(GenerationRun.Trigger.MANUAL);
        taskId = taskService.search(List.of(Task.Status.OPEN), null, null, null, null, 1, 10).getFirst().taskId();
    }

    @Test
    void workingATaskThroughToCompletedRecordsEveryStep() {
        taskService.update(taskId, new TaskUpdateRequest("nurse.kim", null, "nurse.kim", null));
        taskService.update(taskId, new TaskUpdateRequest("nurse.kim", Task.Status.IN_PROGRESS, null, "calling the patient"));
        TaskDto done = taskService.update(taskId,
                new TaskUpdateRequest("nurse.kim", Task.Status.COMPLETED, null, "booked for 2026-10-05")).orElseThrow();

        assertEquals(Task.Status.COMPLETED, done.status());
        assertEquals("nurse.kim", done.closedBy());
        assertEquals("booked for 2026-10-05", done.closeReason());
        assertEquals(List.of("null->OPEN SYSTEM", "OPEN->OPEN nurse.kim assigned to nurse.kim",
                        "OPEN->IN_PROGRESS nurse.kim calling the patient", "IN_PROGRESS->COMPLETED nurse.kim booked for 2026-10-05"),
                done.events().stream().map(TaskServiceTest::describe).toList());
    }

    @Test
    void inProgressCanGoBackToOpen() {
        taskService.update(taskId, new TaskUpdateRequest("nurse.kim", Task.Status.IN_PROGRESS, null, null));

        assertEquals(Task.Status.OPEN,
                taskService.update(taskId, new TaskUpdateRequest("nurse.kim", Task.Status.OPEN, null, "no answer")).orElseThrow().status());
    }

    @Test
    void peopleCannotResolveTasksThatIsTheSystemsStatus() {
        InvalidTaskChangeException e = assertThrows(InvalidTaskChangeException.class,
                () -> taskService.update(taskId, new TaskUpdateRequest("nurse.kim", Task.Status.RESOLVED, null, null)));

        assertFalse(e.conflict());
        assertEquals("A OPEN task can change to [CANCELLED, COMPLETED, IN_PROGRESS], not RESOLVED", e.getMessage());
    }

    @Test
    void aClosedTaskCannotChange() {
        taskService.update(taskId, new TaskUpdateRequest("dr.lee", Task.Status.CANCELLED, null, "patient declined"));

        InvalidTaskChangeException e = assertThrows(InvalidTaskChangeException.class,
                () -> taskService.update(taskId, new TaskUpdateRequest("dr.lee", Task.Status.OPEN, null, null)));

        assertTrue(e.conflict());
    }

    @Test
    void anEmptyChangeIsRejected() {
        assertThrows(InvalidTaskChangeException.class,
                () -> taskService.update(taskId, new TaskUpdateRequest("nurse.kim", null, null, null)));
    }

    @Test
    void anEmptyAssigneeUnassigns() {
        taskService.update(taskId, new TaskUpdateRequest("nurse.kim", null, "nurse.kim", null));

        TaskDto task = taskService.update(taskId, new TaskUpdateRequest("nurse.kim", null, "", null)).orElseThrow();

        assertNull(task.assignee());
        assertEquals("unassigned", task.events().getLast().reason());
    }

    @Test
    void anUnknownTaskIsEmpty() {
        assertTrue(taskService.update(999L, new TaskUpdateRequest("nurse.kim", Task.Status.IN_PROGRESS, null, null)).isEmpty());
        assertTrue(taskService.find(999L).isEmpty());
    }

    @Test
    void taskTypesAreReferenceData() {
        assertEquals(List.of("REFERRAL", "SCHEDULING"), taskService.taskTypes().stream().map(t -> t.code()).toList());
    }

    private static String describe(TaskEventDto event) {
        return event.fromStatus() + "->" + event.toStatus() + " " + event.actor() + (event.reason() == null || event.actor().equals("SYSTEM") ? "" : " " + event.reason());
    }
}
