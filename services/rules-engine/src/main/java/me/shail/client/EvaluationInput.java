package me.shail.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import me.shail.rules.PatientFacts;

/**
 * One patient from clinical-data's {@code GET /api/v1/evaluation-inputs}.
 */
public record EvaluationInput(
        Long patientId,
        String sourcePatientId,
        LocalDate dateOfBirth,
        Character gender,
        List<Diagnosis> diagnoses,
        List<LabResult> latestLabResults,
        List<Visits> visits) {

    public record Diagnosis(String icdCode, String conditionGroup, boolean chronic, LocalDate diagnosedDate) {
    }

    public record LabResult(String testName, BigDecimal resultValue, LocalDate resultDate) {
    }

    public record Visits(String specialty, LocalDate lastVisitDate, LocalDate nextScheduledDate) {
    }

    /** The facts the rules evaluate; test and specialty names are keyed case-insensitively. */
    public PatientFacts toFacts() {
        return new PatientFacts(
                sourcePatientId,
                dateOfBirth,
                diagnoses == null ? List.of() : diagnoses.stream()
                        .map(d -> new PatientFacts.Diagnosis(d.icdCode(), d.conditionGroup(), d.chronic()))
                        .toList(),
                latestLabResults == null ? java.util.Map.of() : latestLabResults.stream().collect(Collectors.toMap(
                        l -> PatientFacts.key(l.testName()),
                        l -> new PatientFacts.LabResult(l.testName(), l.resultValue(), l.resultDate()),
                        (a, b) -> a.date().isAfter(b.date()) ? a : b)),
                visits == null ? java.util.Map.of() : visits.stream().collect(Collectors.toMap(
                        v -> PatientFacts.key(v.specialty()),
                        v -> new PatientFacts.Visits(v.lastVisitDate(), v.nextScheduledDate()),
                        EvaluationInput::mergeVisits)));
    }

    /** Specialty names that differ only in case in the data are one specialty. */
    private static PatientFacts.Visits mergeVisits(PatientFacts.Visits a, PatientFacts.Visits b) {
        return new PatientFacts.Visits(latest(a.lastVisitDate(), b.lastVisitDate()),
                earliest(a.nextScheduledDate(), b.nextScheduledDate()));
    }

    private static LocalDate latest(LocalDate a, LocalDate b) {
        return a == null ? b : b == null ? a : a.isAfter(b) ? a : b;
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        return a == null ? b : b == null ? a : a.isBefore(b) ? a : b;
    }
}
