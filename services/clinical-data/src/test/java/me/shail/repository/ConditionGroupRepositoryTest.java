package me.shail.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.util.List;
import me.shail.support.ReactiveSessions;
import org.junit.jupiter.api.Test;

/** Condition groups are reference data seeded by V4, so these tests read them as-is. */
@QuarkusTest
class ConditionGroupRepositoryTest {

    @Inject
    ConditionGroupRepository repository;

    @Inject
    ReactiveSessions sessions;

    @Test
    @RunOnVertxContext
    void findAllOrderedByPrefixReturnsTheTenSeededChronicFamilies(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findAllOrderedByPrefix()), groups -> {
            assertEquals(List.of("E03", "E10", "E11", "E78", "G47.3", "I10", "I25", "J45", "M81", "N18"),
                    groups.stream().map(g -> g.icdPrefix).toList());
            assertTrue(groups.stream().allMatch(g -> g.chronic));
        });
    }

    @Test
    @RunOnVertxContext
    void findByIcdPrefixMatchesExactly(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findByIcdPrefix("G47.3")),
                group -> assertEquals("Sleep Apnea", group.conditionName));
    }
}
