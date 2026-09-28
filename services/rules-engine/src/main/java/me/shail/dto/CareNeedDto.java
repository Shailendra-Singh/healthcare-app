package me.shail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import me.shail.model.CareNeed;
import me.shail.rules.ProgramEvaluator.NeedStatus;

/**
 * @param lastVisitDate     null when never seen
 * @param nextScheduledDate null when nothing is booked
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
        @JsonInclude(JsonInclude.Include.NON_NULL) String note) {

    public static CareNeedDto from(CareNeed need) {
        return new CareNeedDto(need.sourcePatientId, need.programId, need.tierId, need.specialty, need.everyDays,
                need.lastVisitDate, need.dueDate, need.nextScheduledDate, need.status, need.priority, need.note);
    }
}
