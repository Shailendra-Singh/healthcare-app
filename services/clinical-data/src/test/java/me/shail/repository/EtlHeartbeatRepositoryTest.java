package me.shail.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import me.shail.model.EtlHeartbeat;
import me.shail.support.ReactiveSessions;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EtlHeartbeatRepositoryTest {

    @Inject
    EtlHeartbeatRepository repository;

    @Inject
    ReactiveSessions sessions;

    @Inject
    TestDatabase db;

    @BeforeEach
    void reset() {
        db.reset();
    }

    @Test
    @RunOnVertxContext
    void findByIdIsNullBeforeTheEtlFirstChecks(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findById(EtlHeartbeat.ID)), heartbeat -> assertNull(heartbeat));
    }

    @Test
    @RunOnVertxContext
    void findByIdReturnsTheLatestHeartbeat(UniAsserter asserter) {
        // Same upsert the ETL runs after every check
        String upsert = """
                INSERT INTO etl.heartbeat (heartbeat_id, checked_at, outcome, detail, interval_seconds)
                VALUES (1, now(), ?, ?, ?)
                ON CONFLICT (heartbeat_id) DO UPDATE
                SET checked_at = EXCLUDED.checked_at, outcome = EXCLUDED.outcome,
                    detail = EXCLUDED.detail, interval_seconds = EXCLUDED.interval_seconds""";
        db.update(upsert, "waiting", "Waiting for labs.csv in /data", 300);
        db.update(upsert, "unchanged", "Files unchanged since run 3", 120);

        asserter.assertThat(() -> sessions.read(() -> repository.findById(EtlHeartbeat.ID)), heartbeat -> {
            assertEquals("unchanged", heartbeat.outcome);
            assertEquals("Files unchanged since run 3", heartbeat.detail);
            assertEquals(120, heartbeat.intervalSeconds);
        });
        asserter.execute(() -> assertEquals(1L, db.queryForLong("SELECT count(*) FROM etl.heartbeat")));
    }

    @Test
    void theTableHoldsASingleRow() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> db.update(
                "INSERT INTO etl.heartbeat (heartbeat_id, checked_at, outcome, interval_seconds) VALUES (2, now(), 'busy', 60)"));

        assertEquals(true, e.getCause().getMessage().contains("ck_heartbeat_single_row"), e.getCause().getMessage());
    }
}
