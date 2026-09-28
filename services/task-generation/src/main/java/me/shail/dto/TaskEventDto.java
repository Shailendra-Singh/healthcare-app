package me.shail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import me.shail.model.Task;
import me.shail.model.TaskEvent;

/** @param fromStatus absent for the event that created the task */
public record TaskEventDto(
        OffsetDateTime at,
        @JsonInclude(JsonInclude.Include.NON_NULL) Task.Status fromStatus,
        Task.Status toStatus,
        String actor,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {

    public static TaskEventDto from(TaskEvent event) {
        return new TaskEventDto(event.at, event.fromStatus, event.toStatus, event.actor, event.reason);
    }
}
