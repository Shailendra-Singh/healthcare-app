package me.shail.client;

import java.time.LocalDate;

/**
 * A care need from the rules-engine's {@code GET /api/v1/evaluations/{runId}/care-needs}.
 *
 * @param everyDays     the cadence
 * @param lastVisitDate null when the patient has never seen that specialty
 * @param status        MET, SCHEDULED or OVERDUE
 * @param tasks         the program's task policy; null when the program has none
 */
public record CareNeed(
        String sourcePatientId,
        String programId,
        String tierId,
        String specialty,
        int everyDays,
        LocalDate lastVisitDate,
        LocalDate dueDate,
        LocalDate nextScheduledDate,
        String status,
        String priority,
        String note,
        TaskPolicy tasks) {

    /** Values: scheduling, referral or none. */
    public record TaskPolicy(String pastCadence, String neverSeen) {
    }
}
