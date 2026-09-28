package me.shail.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import me.shail.model.EvaluationRun;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EvaluationRunRepositoryTest {

    @Inject
    EvaluationRunRepository repository;

    @Inject
    TestDatabase db;

    @BeforeEach
    void reset() {
        db.reset();
    }

    @Test
    void theDatabaseAllowsOnlyOneRunningRun() {
        insert(EvaluationRun.Status.RUNNING);

        assertThrows(Exception.class, () -> insert(EvaluationRun.Status.RUNNING));
        assertEquals(1, db.queryForLong("SELECT count(*) FROM eval.evaluation_run"));
    }

    @Test
    void latestAndLatestSucceeded() {
        Long succeeded = insert(EvaluationRun.Status.SUCCEEDED);
        Long failed = insert(EvaluationRun.Status.FAILED);

        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(failed, repository.findLatest().orElseThrow().id);
            assertEquals(succeeded, repository.findLatestSucceeded().orElseThrow().id);
        });
    }

    @Test
    void failAbandonedClosesRunningRunsOnly() {
        insert(EvaluationRun.Status.SUCCEEDED);
        Long running = insert(EvaluationRun.Status.RUNNING);

        int closed = QuarkusTransaction.requiringNew().call(() -> repository.failAbandoned(OffsetDateTime.now()));

        assertEquals(1, closed);
        QuarkusTransaction.requiringNew().run(() -> {
            EvaluationRun run = repository.findById(running).orElseThrow();
            assertEquals(EvaluationRun.Status.FAILED, run.status);
            assertTrue(run.errorMessage.startsWith("Abandoned"));
            assertEquals(0, repository.countRunning());
        });
    }

    @Test
    void deleteAllButNewestKeepsTheNewestRuns() {
        for (int i = 0; i < 5; i++) {
            insert(EvaluationRun.Status.SUCCEEDED);
        }

        int deleted = QuarkusTransaction.requiringNew().call(() -> repository.deleteAllButNewest(2));

        assertEquals(3, deleted);
        assertEquals(4, db.queryForLong("SELECT min(run_id) FROM eval.evaluation_run"));
    }

    private Long insert(EvaluationRun.Status status) {
        return QuarkusTransaction.requiringNew().call(() -> {
            EvaluationRun run = new EvaluationRun();
            run.trigger = EvaluationRun.Trigger.MANUAL;
            run.asOfDate = LocalDate.now();
            run.status = status;
            run.startedAt = OffsetDateTime.now();
            repository.insert(run);
            return run.id;
        });
    }
}
