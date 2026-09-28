package me.shail.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import java.util.Arrays;
import java.util.List;
import me.shail.dto.TaskDto;
import me.shail.dto.TaskUpdateRequest;
import me.shail.model.Task;
import me.shail.repository.TaskRepository;
import me.shail.service.TaskService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/tasks")
@Tag(name = "Tasks")
public class TaskResource {

    @Inject
    TaskService taskService;

    @GET
    @Operation(summary = "Task work list",
            description = "Most urgent first (due date, then high priority). `status` is ACTIVE (OPEN and IN_PROGRESS, the "
                    + "default), ALL, or one status; the other filters are optional. `page` starts at 1.")
    public List<TaskDto> list(
            @RestQuery @DefaultValue("ACTIVE")
            @Pattern(regexp = "ACTIVE|ALL|OPEN|IN_PROGRESS|COMPLETED|CANCELLED|RESOLVED",
                    message = "must be ACTIVE, ALL, OPEN, IN_PROGRESS, COMPLETED, CANCELLED or RESOLVED") String status,
            @RestQuery String taskType,
            @RestQuery String programId,
            @RestQuery String specialty,
            @RestQuery String assignee,
            @RestQuery @DefaultValue("1") @Min(1) int page,
            @RestQuery @DefaultValue("100") @Min(1) @Max(1000) int size) {
        List<Task.Status> statuses = switch (status) {
            case "ACTIVE" -> TaskRepository.ACTIVE;
            case "ALL" -> Arrays.asList(Task.Status.values());
            default -> List.of(Task.Status.valueOf(status));
        };
        return taskService.search(statuses, taskType, programId, specialty, assignee, page, size);
    }

    @GET
    @Path("/{taskId}")
    @Operation(summary = "A task with its history")
    public TaskDto get(@RestPath Long taskId) {
        return taskService.find(taskId).orElseThrow(NotFoundException::new);
    }

    @PATCH
    @Path("/{taskId}")
    @Operation(summary = "Change a task's status or assignee",
            description = "Allowed: OPEN to IN_PROGRESS, COMPLETED or CANCELLED; IN_PROGRESS to OPEN, COMPLETED or "
                    + "CANCELLED. A closed task cannot change (409). `actor` is recorded in the history. A completed "
                    + "or cancelled task is not recreated while the patient's last visit stays the same.")
    public TaskDto update(@RestPath Long taskId, @Valid @NotNull TaskUpdateRequest change) {
        // Refused changes are turned into 400/409 responses with a message by ErrorMappers
        return taskService.update(taskId, change).orElseThrow(NotFoundException::new);
    }
}
