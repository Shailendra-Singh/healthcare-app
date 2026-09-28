package me.shail.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the rules know about one patient. Lab tests and specialties are keyed in lower case, so rules
 * match them case-insensitively.
 *
 * @param latestLabs latest result per test
 * @param visits     last visit and next appointment per specialty
 */
public record PatientFacts(
        String sourcePatientId,
        LocalDate dateOfBirth,
        List<Diagnosis> diagnoses,
        Map<String, LabResult> latestLabs,
        Map<String, Visits> visits) {

    /**
     * @param conditionGroup ICD prefix of the condition group, or null when the code is in none
     */
    public record Diagnosis(String icdCode, String conditionGroup, boolean chronic) {
    }

    public record LabResult(String testName, BigDecimal value, LocalDate date) {
    }

    /**
     * @param lastVisitDate     last visit on or before the evaluation date, or null if never seen
     * @param nextScheduledDate next appointment after the evaluation date, or null if none is booked
     */
    public record Visits(LocalDate lastVisitDate, LocalDate nextScheduledDate) {
    }

    public LabResult latestLab(String testName) {
        return latestLabs.get(key(testName));
    }

    public Visits visits(String specialty) {
        return visits.getOrDefault(key(specialty), new Visits(null, null));
    }

    public static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
