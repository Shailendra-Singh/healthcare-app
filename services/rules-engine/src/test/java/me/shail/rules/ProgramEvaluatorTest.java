package me.shail.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import me.shail.rules.ProgramEvaluator.NeedStatus;
import me.shail.support.Patients;
import org.junit.jupiter.api.Test;

class ProgramEvaluatorTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 9, 28);
    static final ProgramDefinition.Need PCP_YEARLY = new ProgramDefinition.Need("PCP", 365, "normal", null);

    @Test
    void needIsMetWhileTheIntervalRuns() {
        var result = ProgramEvaluator.need(PCP_YEARLY,
                Patients.aged(40, AS_OF).visits("pcp", AS_OF.minusDays(364), null).build(), AS_OF);

        assertEquals(NeedStatus.MET, result.status());
        assertEquals(AS_OF.plusDays(1), result.dueDate());
    }

    @Test
    void needIsOverdueOnTheDueDateWithNothingBooked() {
        var result = ProgramEvaluator.need(PCP_YEARLY,
                Patients.aged(40, AS_OF).visits("PCP", AS_OF.minusDays(365), null).build(), AS_OF);

        assertEquals(NeedStatus.OVERDUE, result.status());
        assertEquals(AS_OF, result.dueDate());
    }

    @Test
    void needIsScheduledWhenDueButBooked() {
        LocalDate booked = AS_OF.plusDays(20);
        var result = ProgramEvaluator.need(PCP_YEARLY,
                Patients.aged(40, AS_OF).visits("PCP", AS_OF.minusDays(500), booked).build(), AS_OF);

        assertEquals(NeedStatus.SCHEDULED, result.status());
        assertEquals(booked, result.nextScheduledDate());
    }

    @Test
    void neverSeenIsDueToday() {
        var result = ProgramEvaluator.need(PCP_YEARLY, Patients.aged(40, AS_OF).build(), AS_OF);

        assertEquals(NeedStatus.OVERDUE, result.status());
        assertNull(result.lastVisitDate());
        assertEquals(AS_OF, result.dueDate());
    }

    @Test
    void eligiblePatientWithNoMatchingTierHasNoTierOrNeeds() {
        ProgramDefinition program = new ProgramDefinition("p", "P", null, null, new Condition.Age(18, null),
                List.of(new ProgramDefinition.Tier("seniors", "Seniors", new Condition.Age(65, null), List.of(PCP_YEARLY))),
                null);

        var result = ProgramEvaluator.evaluate(program, Patients.aged(40, AS_OF).build(), AS_OF).orElseThrow();

        assertNull(result.tier());
        assertTrue(result.needs().isEmpty());
        assertEquals(40, result.evidence().get("age"));
    }
}
