package me.shail.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import me.shail.rules.PatientFacts;
import org.junit.jupiter.api.Test;

class EvaluationInputTest {

    @Test
    void toFactsKeysTestsAndSpecialtiesCaseInsensitively() {
        EvaluationInput input = new EvaluationInput(1L, "P1", LocalDate.of(1960, 5, 5), 'F',
                List.of(new EvaluationInput.Diagnosis("E11.65", "E11", true, LocalDate.of(2020, 1, 1))),
                List.of(new EvaluationInput.LabResult("HbA1c", new BigDecimal("9.4"), LocalDate.of(2026, 5, 2))),
                List.of(new EvaluationInput.Visits("Endocrinology", LocalDate.of(2026, 1, 1), null)));

        PatientFacts facts = input.toFacts();

        assertEquals("P1", facts.sourcePatientId());
        assertEquals(List.of(new PatientFacts.Diagnosis("E11.65", "E11", true)), facts.diagnoses());
        assertEquals(new BigDecimal("9.4"), facts.latestLab("HBA1C").value());
        assertEquals(LocalDate.of(2026, 1, 1), facts.visits("endocrinology").lastVisitDate());
    }

    @Test
    void specialtiesDifferingOnlyInCaseAreMerged() {
        EvaluationInput input = new EvaluationInput(1L, "P1", LocalDate.of(1960, 5, 5), 'F', List.of(), List.of(),
                List.of(new EvaluationInput.Visits("PCP", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 1)),
                        new EvaluationInput.Visits("pcp", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 1))));

        PatientFacts.Visits visits = input.toFacts().visits("PCP");

        assertEquals(LocalDate.of(2026, 3, 1), visits.lastVisitDate(), "latest past visit");
        assertEquals(LocalDate.of(2026, 11, 1), visits.nextScheduledDate(), "earliest future appointment");
    }

    @Test
    void missingListsBecomeEmptyFacts() {
        PatientFacts facts = new EvaluationInput(1L, "P1", null, null, null, null, null).toFacts();

        assertTrue(facts.diagnoses().isEmpty());
        assertTrue(facts.latestLabs().isEmpty());
        assertTrue(facts.visits().isEmpty());
    }
}
