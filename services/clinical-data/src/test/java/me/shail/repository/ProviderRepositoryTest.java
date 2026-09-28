package me.shail.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.model.Provider;
import me.shail.support.ReactiveSessions;
import me.shail.support.TestData;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProviderRepositoryTest {

    @Inject
    ProviderRepository repository;

    @Inject
    ReactiveSessions sessions;

    @Inject
    TestDatabase db;

    // Prefixes make the expected sort order independent of the fake names and the DB collation
    String alpha;
    String bravo;
    String charlie;
    Long bravoId;

    @BeforeEach
    void seed() {
        db.reset();
        alpha = "A " + TestData.FAKER.name().lastName();
        bravo = "B " + TestData.FAKER.name().lastName();
        charlie = "C " + TestData.FAKER.name().lastName();
        db.insertProvider(charlie);
        bravoId = db.insertProvider(bravo);
        db.insertProvider(alpha);
    }

    @Test
    @RunOnVertxContext
    void findByIdReturnsTheRow(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findById(bravoId)),
                found -> assertEquals(bravo, found.name));
    }

    @Test
    @RunOnVertxContext
    void findByIdReturnsNullWhenMissing(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findById(-1L)), found -> assertNull(found));
    }

    @Test
    @RunOnVertxContext
    void findAllOrderedByNameSortsByName(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findAllOrderedByName()),
                all -> assertEquals(List.of(alpha, bravo, charlie), all.stream().map(e -> e.name).toList()));
    }

    @Test
    @RunOnVertxContext
    void findByNameIgnoresCase(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findByName(bravo.toUpperCase())),
                found -> assertEquals(bravoId, found.id));
    }

    @Test
    @RunOnVertxContext
    void findByNameFailsWithNoResultWhenMissing(UniAsserter asserter) {
        asserter.assertFailedWith(() -> sessions.read(() -> repository.findByName("no such name")),
                e -> assertInstanceOf(NoResultException.class, e));
    }
}
