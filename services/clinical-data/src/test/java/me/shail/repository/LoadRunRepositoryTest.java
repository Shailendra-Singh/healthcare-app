package me.shail.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import me.shail.support.ReactiveSessions;
import me.shail.support.TestData;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Covers LoadRunRepository, LoadFileRepository and LoadRejectRepository against the real etl tables. */
@QuarkusTest
class LoadRunRepositoryTest {

    @Inject
    LoadRunRepository loadRunRepository;

    @Inject
    LoadFileRepository loadFileRepository;

    @Inject
    LoadRejectRepository loadRejectRepository;

    @Inject
    ReactiveSessions sessions;

    @Inject
    TestDatabase db;

    String labsChecksum;

    @BeforeEach
    void reset() {
        db.reset();
        labsChecksum = TestData.FAKER.hashing().sha256();
    }

    @Test
    @RunOnVertxContext
    void findLatestFailsWithNoResultWhenThereAreNoRuns(UniAsserter asserter) {
        asserter.assertFailedWith(() -> sessions.read(() -> loadRunRepository.findLatest()),
                e -> assertInstanceOf(NoResultException.class, e));
    }

    @Test
    @RunOnVertxContext
    void findLatestReturnsTheHighestRunId(UniAsserter asserter) {
        long first = seedRun("SUCCEEDED", null);
        long latest = seedRun("FAILED", "BadCopyFileFormat: column name mismatch");

        asserter.assertThat(() -> sessions.read(() -> loadRunRepository.findLatest()), run -> {
            assertTrue(latest > first);
            assertEquals(latest, run.id);
            assertEquals("FAILED", run.status);
            assertEquals("BadCopyFileFormat: column name mismatch", run.errorMessage);
        });
    }

    @Test
    @RunOnVertxContext
    void findLatestByStatusSkipsNewerRunsWithOtherStatuses(UniAsserter asserter) {
        long succeeded = seedRun("SUCCEEDED", null);
        seedRun("FAILED", "BadCopyFileFormat: column name mismatch");
        seedRun("STARTED", null);

        asserter.assertThat(() -> sessions.read(() -> loadRunRepository.findLatestByStatus("SUCCEEDED")),
                run -> assertEquals(succeeded, run.id));
    }

    @Test
    @RunOnVertxContext
    void findByRunReturnsThatRunsFilesSortedByName(UniAsserter asserter) {
        long other = seedRun("SUCCEEDED", null);
        seedFile(other, "labs.csv", TestData.FAKER.hashing().sha256(), 5);
        long runId = seedRun("SUCCEEDED", null);
        seedFile(runId, "patients.csv", TestData.FAKER.hashing().sha256(), 3);
        seedFile(runId, "labs.csv", labsChecksum, 7);

        asserter.assertThat(() -> sessions.read(() -> loadFileRepository.findByRun(runId)), files -> {
            assertEquals(List.of("labs.csv", "patients.csv"), files.stream().map(f -> f.fileName).toList());
            assertEquals(labsChecksum, files.getFirst().checksum);
            assertEquals(7, files.getFirst().rowCount);
        });
    }

    @Test
    @RunOnVertxContext
    void countByFileGroupsRejectsForOneRun(UniAsserter asserter) {
        long runId = seedRun("SUCCEEDED", null);
        long other = seedRun("SUCCEEDED", null);
        seedReject(runId, "labs.csv");
        seedReject(runId, "labs.csv");
        seedReject(runId, "patients.csv");
        seedReject(other, "labs.csv");

        asserter.assertThat(() -> sessions.read(() -> loadRejectRepository.countByFile(runId)), rows -> {
            Map<String, Long> counts = rows.stream().collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));
            assertEquals(Map.of("labs.csv", 2L, "patients.csv", 1L), counts);
        });
    }

    private long seedRun(String status, String errorMessage) {
        return db.queryForLong("""
                INSERT INTO etl.load_run (status, error_message, finished_at)
                VALUES (?, ?, now()) RETURNING run_id""", status, errorMessage);
    }

    private void seedFile(long runId, String fileName, String checksum, int rows) {
        db.update("""
                INSERT INTO etl.load_file (run_id, file_name, checksum, file_size, row_count)
                VALUES (?, ?, ?, ?, ?)""", runId, fileName, checksum, 2048L, rows);
    }

    private void seedReject(long runId, String fileName) {
        db.update("INSERT INTO etl.load_reject (run_id, file_name, reason, row_data) VALUES (?, ?, ?, '{}'::jsonb)",
                runId, fileName, TestData.FAKER.lorem().sentence());
    }
}
