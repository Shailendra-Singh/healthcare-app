package me.shail.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import me.shail.model.Language;
import me.shail.support.ReactiveSessions;
import me.shail.support.TestData;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The repositories inherit insert/update/delete from RecordRepository. The app's reactive pool is
 * read-only (default_transaction_read_only=on), so PostgreSQL must reject every one of them.
 */
@QuarkusTest
class ReadOnlyDataSourceTest {

    @Inject
    LanguageRepository repository;

    @Inject
    ReactiveSessions sessions;

    @Inject
    TestDatabase db;

    short languageId;

    @BeforeEach
    void seed() {
        db.reset();
        languageId = db.insertLanguage("English");
    }

    @Test
    @RunOnVertxContext
    void insertIsRejected(UniAsserter asserter) {
        Language language = new Language();
        language.name = TestData.FAKER.nation().language() + " (new)";

        asserter.assertFailedWith(() -> sessions.write(() -> repository.insert(language)), e -> assertReadOnlyViolation(e, "INSERT"));
        asserter.execute(() -> assertEquals(1L, db.queryForLong("SELECT count(*) FROM dbo.language")));
    }

    @Test
    @RunOnVertxContext
    void updateIsRejected(UniAsserter asserter) {
        Language language = new Language();
        language.id = languageId;
        language.name = "Changed";

        asserter.assertFailedWith(() -> sessions.write(() -> repository.update(language)), e -> assertReadOnlyViolation(e, "UPDATE"));
        asserter.execute(() -> assertEquals("English",
                db.queryForString("SELECT language_name FROM dbo.language WHERE language_id = ?", languageId)));
    }

    @Test
    @RunOnVertxContext
    void deleteIsRejected(UniAsserter asserter) {
        asserter.assertFailedWith(() -> sessions.write(() -> repository.deleteById(languageId)), e -> assertReadOnlyViolation(e, "DELETE"));
        asserter.execute(() -> assertEquals(1L, db.queryForLong("SELECT count(*) FROM dbo.language")));
    }

    /** PostgreSQL SQLSTATE 25006: read_only_sql_transaction. */
    private static void assertReadOnlyViolation(Throwable e, String statement) {
        String expected = "cannot execute " + statement + " in a read-only transaction";
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(expected)) {
                return;
            }
        }
        fail("Expected '" + expected + "' but got: " + e);
    }
}
