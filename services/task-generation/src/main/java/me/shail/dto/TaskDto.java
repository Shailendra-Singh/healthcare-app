package me.shail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import me.shail.model.Task;

/**
 * A care task. Patient details come from clinical-data by {@code sourcePatientId}.
 *
 * @param cadenceDays   the need's cadence, e.g. 90 for "Endocrinology every 90 days"
 * @param lastVisitDate absent for referrals
 * @param events        the task's history; only on {@code GET /tasks/{taskId}}
 */
public record TaskDto(
        Long taskId,
        String sourcePatientId,
        String programId,
        String tierId,
        String specialty,
        String taskType,
        Task.Status status,
        String priority,
        int cadenceDays,
        LocalDate dueDate,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate lastVisitDate,
        @JsonInclude(JsonInclude.Include.NON_NULL) String note,
        @JsonInclude(JsonInclude.Include.NON_NULL) String assignee,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime closedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String closedBy,
        @JsonInclude(JsonInclude.Include.NON_NULL) String closeReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<TaskEventDto> events) {

    public static TaskDto from(Task task, List<TaskEventDto> events) {
        return new TaskDto(task.id, task.sourcePatientId, task.programId, task.tierId, task.specialty, task.taskType,
                task.status, task.priority, task.cadenceDays, task.dueDate, task.lastVisitDate, task.note,
                task.assignee, task.createdAt, task.updatedAt, task.closedAt, task.closedBy, task.closeReason, events);
    }

    public static TaskDto from(Task task) {
        return from(task, null);
    }
}
