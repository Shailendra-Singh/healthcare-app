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
import me.shail.support.ReactiveSessions;
import me.shail.support.TestData;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DiagnosisCodeRepositoryTest {

    @Inject
    DiagnosisCodeRepository repository;

    @Inject
    ReactiveSessions sessions;

    @Inject
    TestDatabase db;

    String diabetesDescription;

    @BeforeEach
    void seed() {
        db.reset();
        diabetesDescription = TestData.FAKER.disease().anyDisease();
        db.insertDiagnosisCode("I10", TestData.FAKER.disease().anyDisease(), "I10");
        db.insertDiagnosisCode("E11.65", diabetesDescription, "E11");
        db.insertDiagnosisCode("Z00.00", TestData.FAKER.disease().anyDisease(), null);
    }

    @Test
    @RunOnVertxContext
    void findWithGroupFetchesTheConditionGroup(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findWithGroup("E11.65")), code -> {
            assertEquals(diabetesDescription, code.description);
            assertEquals("E11", code.conditionGroup.icdPrefix);
        });
    }

    @Test
    @RunOnVertxContext
    void findWithGroupReturnsCodesOutsideAnyFamily(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findWithGroup("Z00.00")),
                code -> assertNull(code.conditionGroup));
    }

    @Test
    @RunOnVertxContext
    void findWithGroupFailsWithNoResultWhenMissing(UniAsserter asserter) {
        asserter.assertFailedWith(() -> sessions.read(() -> repository.findWithGroup("Z99.9")),
                e -> assertInstanceOf(NoResultException.class, e));
    }

    @Test
    @RunOnVertxContext
    void findAllWithGroupSortsByCode(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findAllWithGroup()),
                codes -> assertEquals(List.of("E11.65", "I10", "Z00.00"), codes.stream().map(c -> c.icdCode).toList()));
    }

    @Test
    @RunOnVertxContext
    void findChronicLeavesOutCodesWithoutAChronicFamily(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findChronic()),
                codes -> assertEquals(List.of("E11.65", "I10"), codes.stream().map(c -> c.icdCode).toList()));
    }
}
