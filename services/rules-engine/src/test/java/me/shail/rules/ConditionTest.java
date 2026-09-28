package me.shail.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.shail.support.Patients;
import org.junit.jupiter.api.Test;

class ConditionTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 9, 28);

    Map<String, Object> evidence = new LinkedHashMap<>();

    @Test
    void ageBoundsAreInclusiveAndUseTheBirthday() {
        Condition adult = new Condition.Age(18, null);
        assertTrue(adult.matches(Patients.bornOn(AS_OF.minusYears(18)).build(), AS_OF, evidence), "18th birthday today");
        assertEquals(18, evidence.get("age"));
        assertFalse(adult.matches(Patients.bornOn(AS_OF.minusYears(18).plusDays(1)).build(), AS_OF, new LinkedHashMap<>()),
                "turns 18 tomorrow");
        Condition youngAdult = new Condition.Age(18, 64);
        assertTrue(youngAdult.matches(Patients.aged(64, AS_OF).build(), AS_OF, new LinkedHashMap<>()));
        assertFalse(youngAdult.matches(Patients.aged(65, AS_OF).build(), AS_OF, new LinkedHashMap<>()));
    }

    @Test
    void diagnosisByChronicFlagConditionGroupOrIcdPrefix() {
        PatientFacts patient = Patients.aged(50, AS_OF)
                .diagnosis("I10", "I10", true)
                .diagnosis("Z00.00", null, false)
                .build();

        assertTrue(new Condition.Diagnosis(true, null, null).matches(patient, AS_OF, evidence));
        assertEquals(List.of("I10"), evidence.get("diagnoses"));
        assertFalse(new Condition.Diagnosis(null, List.of("E10", "E11"), null).matches(patient, AS_OF, new LinkedHashMap<>()));
        assertTrue(new Condition.Diagnosis(null, List.of("i10"), null).matches(patient, AS_OF, new LinkedHashMap<>()),
                "groups match case-insensitively");
        assertTrue(new Condition.Diagnosis(null, null, List.of("z00")).matches(patient, AS_OF, new LinkedHashMap<>()),
                "prefixes cover codes outside any group");
        assertFalse(new Condition.Diagnosis(true, null, List.of("Z00")).matches(patient, AS_OF, new LinkedHashMap<>()),
                "every given field must hold for the same diagnosis");
    }

    @Test
    void labWindowIsCalendarMonthsBackFromTodayInclusive() {
        Condition recent = new Condition.Lab("HbA1c", 6, null, true);
        assertTrue(recent.matches(Patients.aged(50, AS_OF).lab("HbA1c", "8.0", LocalDate.of(2026, 3, 28)).build(), AS_OF, evidence),
                "exactly 6 calendar months ago counts");
        assertEquals(Map.of("value", new BigDecimal("8.0"), "date", "2026-03-28"), evidence.get("HbA1c"));
        assertFalse(recent.matches(Patients.aged(50, AS_OF).lab("HbA1c", "8.0", LocalDate.of(2026, 3, 27)).build(), AS_OF,
                new LinkedHashMap<>()), "one day older is outside the window");
    }

    @Test
    void labRangeChecksTheLatestResultInTheWindow() {
        Condition high = new Condition.Lab("HbA1c", 6, new Condition.Range(null, new BigDecimal("9.0"), null, null), null);
        Condition moderate = new Condition.Lab("HbA1c", 6,
                new Condition.Range(null, new BigDecimal("7.0"), new BigDecimal("9.0"), null), null);

        PatientFacts nine = Patients.aged(50, AS_OF).lab("hba1c", "9.0", AS_OF.minusMonths(1)).build();
        assertTrue(high.matches(nine, AS_OF, new LinkedHashMap<>()), "9.0 is high (>=), test name case-insensitive");
        assertFalse(moderate.matches(nine, AS_OF, new LinkedHashMap<>()), "9.0 is not moderate (<9.0)");

        PatientFacts oldHigh = Patients.aged(50, AS_OF).lab("HbA1c", "11.0", AS_OF.minusMonths(7)).build();
        assertFalse(high.matches(oldHigh, AS_OF, new LinkedHashMap<>()), "a result outside the window does not count");
    }

    @Test
    void labExistsFalseMatchesWhenNothingIsInTheWindow() {
        Condition unmonitored = new Condition.Lab("HbA1c", 6, null, false);

        assertTrue(unmonitored.matches(Patients.aged(50, AS_OF).build(), AS_OF, evidence));
        assertEquals("no result in the last 6 months", evidence.get("HbA1c"));
        assertTrue(unmonitored.matches(Patients.aged(50, AS_OF).lab("HbA1c", "7.5", AS_OF.minusMonths(8)).build(), AS_OF,
                new LinkedHashMap<>()));
        assertFalse(unmonitored.matches(Patients.aged(50, AS_OF).lab("HbA1c", "7.5", AS_OF.minusMonths(2)).build(), AS_OF,
                new LinkedHashMap<>()));
    }

    @Test
    void anyStopsAtTheFirstMatchAndKeepsOnlyItsEvidence() {
        Condition any = new Condition.Any(List.of(new Condition.Age(65, null), new Condition.Diagnosis(true, null, null)));
        PatientFacts chronicAt40 = Patients.aged(40, AS_OF).diagnosis("E78.5", "E78", true).build();

        assertTrue(any.matches(chronicAt40, AS_OF, evidence));
        assertEquals(Map.of("diagnoses", List.of("E78.5")), evidence);
        assertFalse(any.matches(Patients.aged(40, AS_OF).build(), AS_OF, new LinkedHashMap<>()));
    }

    @Test
    void allNeedsEveryConditionAndAddsNoEvidenceWhenItFails() {
        Condition all = new Condition.All(List.of(new Condition.Age(65, null), new Condition.Diagnosis(true, null, null)));

        assertFalse(all.matches(Patients.aged(70, AS_OF).build(), AS_OF, evidence));
        assertTrue(evidence.isEmpty(), "partial evidence from a failed all is discarded");
        assertTrue(all.matches(Patients.aged(70, AS_OF).diagnosis("I10", "I10", true).build(), AS_OF, evidence));
        assertEquals(70, evidence.get("age"));
    }

    @Test
    void notInvertsWithoutEvidence() {
        Condition notSenior = new Condition.Not(new Condition.Age(65, null));

        assertTrue(notSenior.matches(Patients.aged(30, AS_OF).build(), AS_OF, evidence));
        assertTrue(evidence.isEmpty());
        assertFalse(notSenior.matches(Patients.aged(80, AS_OF).build(), AS_OF, evidence));
    }
}
