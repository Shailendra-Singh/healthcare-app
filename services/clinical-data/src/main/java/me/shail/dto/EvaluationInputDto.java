package me.shail.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the rules-engine needs to evaluate one patient against the care programs.
 *
 * @param latestLabResults each test's most recent result
 * @param visits           per specialty, the last visit on or before the as-of date and the next appointment after it
 */
public record EvaluationInputDto(
        Long patientId,
        String sourcePatientId,
        LocalDate dateOfBirth,
        Character gender,
        List<Diagnosis> diagnoses,
        List<LabResult> latestLabResults,
        List<Visits> visits) {

    /**
     * @param conditionGroup ICD prefix of the condition group (e.g. E11), or null when the code is in none
     * @param chronic        true when the condition group is a chronic family
     */
    public record Diagnosis(String icdCode, String conditionGroup, boolean chronic, LocalDate diagnosedDate) {
    }

    public record LabResult(String testName, BigDecimal resultValue, LocalDate resultDate) {
    }

    public record Visits(String specialty, LocalDate lastVisitDate, LocalDate nextScheduledDate) {
    }
}
