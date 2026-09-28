package me.shail.rules;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies one care program to one patient: eligibility, then the first matching tier, then the status of
 * each of that tier's needs.
 */
public final class ProgramEvaluator {

    private ProgramEvaluator() {
    }

    public enum NeedStatus {
        /** Last visit + interval is still in the future. */
        MET,
        /** Due today or earlier, but an appointment is booked. */
        SCHEDULED,
        /** Due today or earlier and nothing is booked; a patient never seen is due today. */
        OVERDUE
    }

    /**
     * @param tier     null when the patient is eligible but no tier matches (a program without an
     *                 {@code otherwise} tier)
     * @param evidence what matched: eligibility and tier criteria, e.g. {@code {age: 67}}
     */
    public record Result(ProgramDefinition program, ProgramDefinition.Tier tier, Map<String, Object> evidence,
            List<NeedResult> needs) {
    }

    public record NeedResult(ProgramDefinition.Need need, LocalDate lastVisitDate, LocalDate dueDate,
            LocalDate nextScheduledDate, NeedStatus status) {
    }

    /** Empty when the patient is not eligible. */
    public static Optional<Result> evaluate(ProgramDefinition program, PatientFacts patient, LocalDate asOf) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        if (!program.eligibility().matches(patient, asOf, evidence)) {
            return Optional.empty();
        }
        for (ProgramDefinition.Tier tier : program.tiers()) {
            Map<String, Object> tierEvidence = new LinkedHashMap<>(evidence);
            if (tier.criteria().matches(patient, asOf, tierEvidence)) {
                List<NeedResult> needs = tier.needs().stream().map(need -> need(need, patient, asOf)).toList();
                return Optional.of(new Result(program, tier, tierEvidence, needs));
            }
        }
        return Optional.of(new Result(program, null, evidence, List.of()));
    }

    static NeedResult need(ProgramDefinition.Need need, PatientFacts patient, LocalDate asOf) {
        PatientFacts.Visits visits = patient.visits(need.visit());
        LocalDate due = visits.lastVisitDate() == null ? asOf : visits.lastVisitDate().plusDays(need.everyDays());
        NeedStatus status = due.isAfter(asOf) ? NeedStatus.MET
                : visits.nextScheduledDate() != null ? NeedStatus.SCHEDULED
                : NeedStatus.OVERDUE;
        return new NeedResult(need, visits.lastVisitDate(), due, visits.nextScheduledDate(), status);
    }
}
