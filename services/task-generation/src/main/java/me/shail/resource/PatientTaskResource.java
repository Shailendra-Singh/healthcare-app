package me.shail.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.TaskDto;
import me.shail.service.TaskService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

@Path("/patients/{sourcePatientId}/tasks")
@Tag(name = "Tasks")
public class PatientTaskResource {

    @Inject
    TaskService taskService;

    @GET
    @Operation(summary = "A patient's tasks", description = "Open and closed, newest first, by patient_id from patients.csv.")
    public List<TaskDto> list(@RestPath String sourcePatientId) {
        return taskService.forPatient(sourcePatientId);
    }
}
