package me.shail.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.TaskTypeDto;
import me.shail.service.TaskService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/task-types")
@Tag(name = "Tasks")
public class TaskTypeResource {

    @Inject
    TaskService taskService;

    @GET
    public List<TaskTypeDto> list() {
        return taskService.taskTypes();
    }
}
