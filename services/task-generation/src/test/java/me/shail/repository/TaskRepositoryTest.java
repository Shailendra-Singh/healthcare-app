package me.shail.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import me.shail.model.Task;
import me.shail.support.Needs;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TaskRepositoryTest {

    static final LocalDate DAY1 = LocalDate.of(2026, 9, 28);

    @Inject
    TaskRepository repository;

    @Inject
    TestDatabase db;

    String patient;

    @BeforeEach
    void reset() {
        db.reset();
        patient = Needs.patientId();
    }

    @Test
    void theDatabaseAllowsOneActiveTaskPerPatientProgramSpecialtyAndType() {
        insert("Endocrinology", "SCHEDULING", Task.Status.OPEN, DAY1, null);

        assertThrows(Exception.class, () -> insert("Endocrinology", "SCHEDULING", Task.Status.IN_PROGRESS, DAY1, null));
        insert("Endocrinology", "REFERRAL", Task.Status.OPEN, DAY1, null);
        insert("Endocrinology", "SCHEDULING", Task.Status.CANCELLED, DAY1, null);
        assertEquals(3, db.queryForLong("SELECT count(*) FROM task.task"));
    }

    @Test
    void searchFiltersAndSortsMostUrgentFirst() {
        insert("Podiatry", "REFERRAL", Task.Status.OPEN, DAY1.plusDays(2), null);
        Long urgentHigh = insert("Cardiology", "SCHEDULING", Task.Status.OPEN, DAY1, null, "high");
        Long urgentNormal = insert("Endocrinology", "SCHEDULING", Task.Status.OPEN, DAY1, null, "normal");
        insert("Nephrology", "SCHEDULING", Task.Status.RESOLVED, DAY1.minusDays(9), null);

        QuarkusTransaction.requiringNew().run(() -> {
            List<Task> active = repository.search(TaskRepository.ACTIVE, null, null, null, null, 1, 10);
            assertEquals(List.of("Cardiology", "Endocrinology", "Podiatry"), active.stream().map(t -> t.specialty).toList());
            assertEquals(urgentHigh, active.get(0).id);
            assertEquals(urgentNormal, active.get(1).id);
            assertEquals(List.of("Podiatry"), repository.search(TaskRepository.ACTIVE, "REFERRAL", null, null, null, 1, 10)
                    .stream().map(t -> t.specialty).toList());
            assertEquals(1, repository.search(TaskRepository.ACTIVE, null, null, "cardiology", null, 1, 10).size());
            assertEquals(1, repository.search(TaskRepository.ACTIVE, null, null, null, null, 2, 2).size());
        });
    }

    @Test
    void closedByPersonForSameGapMatchesTheLastVisitIncludingNever() {
        insert("Podiatry", "REFERRAL", Task.Status.CANCELLED, DAY1, null);
        insert("Endocrinology", "SCHEDULING", Task.Status.COMPLETED, DAY1, DAY1.minusDays(100));
        insert("Cardiology", "SCHEDULING", Task.Status.RESOLVED, DAY1, DAY1.minusDays(100));

        QuarkusTransaction.requiringNew().run(() -> {
            assertTrue(repository.closedByPersonForSameGap(patient, "diabetes-management", "Podiatry", "REFERRAL", null));
            assertTrue(repository.closedByPersonForSameGap(patient, "diabetes-management", "Endocrinology", "SCHEDULING", DAY1.minusDays(100)));
            assertFalse(repository.closedByPersonForSameGap(patient, "diabetes-management", "Endocrinology", "SCHEDULING", DAY1.minusDays(50)),
                    "a newer visit is a new gap");
            assertFalse(repository.closedByPersonForSameGap(patient, "diabetes-management", "Cardiology", "SCHEDULING", DAY1.minusDays(100)),
                    "the system resolving a task is not a person's decision");
        });
    }

    private Long insert(String specialty, String taskType, Task.Status status, LocalDate due, LocalDate lastVisit) {
        return insert(specialty, taskType, status, due, lastVisit, "normal");
    }

    private Long insert(String specialty, String taskType, Task.Status status, LocalDate due, LocalDate lastVisit, String priority) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Task task = new Task();
            task.sourcePatientId = patient;
            task.programId = "diabetes-management";
            task.tierId = "high-risk";
            task.specialty = specialty;
            task.taskType = taskType;
            task.status = status;
            task.priority = priority;
            task.cadenceDays = 90;
            task.dueDate = due;
            task.lastVisitDate = lastVisit;
            task.firstEvaluationRunId = 1L;
            task.lastEvaluationRunId = 1L;
            task.createdAt = OffsetDateTime.now();
            task.updatedAt = task.createdAt;
            if (!status.isActive()) {
                task.closedAt = task.createdAt;
                task.closedBy = status == Task.Status.RESOLVED ? Task.SYSTEM : "dr.lee";
            }
            repository.insert(task);
            return task.id;
        });
    }
}
