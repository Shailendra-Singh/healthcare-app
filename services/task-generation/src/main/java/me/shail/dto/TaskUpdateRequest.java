package me.shail.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import me.shail.model.Task;

/**
 * A person's change to a task: a new status, a new assignee, or both.
 *
 * @param actor    who is making the change (recorded in the task's history)
 * @param status   IN_PROGRESS, OPEN (back from in progress), COMPLETED or CANCELLED; absent to keep it
 * @param assignee new assignee; absent to keep it, empty text to unassign
 * @param reason   optional, recorded in the history
 */
public record TaskUpdateRequest(
        @NotBlank @Size(max = 100) String actor,
        Task.Status status,
        @Size(max = 100) String assignee,
        String reason) {
}
