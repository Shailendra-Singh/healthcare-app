package me.shail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import me.shail.model.CareNeed;
import me.shail.model.ProgramVersion;
import me.shail.rules.ProgramEvaluator.NeedStatus;

/**
 * @param lastVisitDate     null when never seen
 * @param nextScheduledDate null when nothing is booked
 * @param tasks             the program's task policy for due needs; absent when the program has none
 */
public record CareNeedDto(
        String sourcePatientId,
        String programId,
        String tierId,
        String specialty,
        int everyDays,
        LocalDate lastVisitDate,
        LocalDate dueDate,
        LocalDate nextScheduledDate,
        NeedStatus status,
        String priority,
        @JsonInclude(JsonInclude.Include.NON_NULL) String note,
        @JsonInclude(JsonInclude.Include.NON_NULL) TaskPolicy tasks) {

    /**
     * @param pastCadence task when seen before and past the cadence: scheduling, referral or none
     * @param neverSeen   task when never seen: scheduling, referral or none
     */
    public record TaskPolicy(String pastCadence, String neverSeen) {

        /** Null when the program version has no policy. */
        public static TaskPolicy of(ProgramVersion version) {
            return version == null || version.pastCadenceTask == null ? null
                    : new TaskPolicy(version.pastCadenceTask, version.neverSeenTask);
        }
    }

    public static CareNeedDto from(CareNeed need, TaskPolicy tasks) {
        return new CareNeedDto(need.sourcePatientId, need.programId, need.tierId, need.specialty, need.everyDays,
                need.lastVisitDate, need.dueDate, need.nextScheduledDate, need.status, need.priority, need.note, tasks);
    }
}
