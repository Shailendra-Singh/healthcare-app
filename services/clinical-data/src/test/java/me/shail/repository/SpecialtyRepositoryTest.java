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
import me.shail.model.Specialty;
import me.shail.support.ReactiveSessions;
import me.shail.support.TestData;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SpecialtyRepositoryTest {

    @Inject
    SpecialtyRepository repository;

    @Inject
    ReactiveSessions sessions;

    @Inject
    TestDatabase db;

    // Prefixes make the expected sort order independent of the fake names and the DB collation
    String alpha;
    String bravo;
    String charlie;
    Short bravoId;

    @BeforeEach
    void seed() {
        db.reset();
        alpha = "A " + TestData.FAKER.medication().drugName();
        bravo = "B " + TestData.FAKER.medication().drugName();
        charlie = "C " + TestData.FAKER.medication().drugName();
        db.insertSpecialty(charlie);
        bravoId = db.insertSpecialty(bravo);
        db.insertSpecialty(alpha);
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
        asserter.assertThat(() -> sessions.read(() -> repository.findById((short) -1)), found -> assertNull(found));
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
