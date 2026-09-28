package me.shail.resource;

import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import me.shail.dto.GenerationRunDto;
import me.shail.model.GenerationRun;
import me.shail.service.NoEvaluationException;
import me.shail.service.TaskGenerationService;
import me.shail.service.TaskService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/generation-runs")
@Tag(name = "Generation runs")
public class GenerationRunResource {

    @Inject
    TaskGenerationService taskGenerationService;

    @Inject
    TaskService taskService;

    @POST
    @Operation(summary = "Reconcile the tasks now",
            description = "Against the rules-engine's latest successful evaluation, in the background: 202 with the new "
                    + "run (RUNNING). 404 when the rules-engine has no successful evaluation yet; 409 while another run is "
                    + "running.")
    public RestResponse<GenerationRunDto> start() {
        // A run already in progress (409) and no evaluation yet (404) are answered by ErrorMappers
        GenerationRun run = taskGenerationService.start(GenerationRun.Trigger.MANUAL).orElseThrow(NoEvaluationException::new);
        Infrastructure.getDefaultWorkerPool().execute(() -> taskGenerationService.execute(run));
        return RestResponse.status(RestResponse.Status.ACCEPTED, GenerationRunDto.from(run));
    }

    @GET
    @Path("/latest")
    public GenerationRunDto latest() {
        return taskService.latestRun().orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/{runId}")
    public GenerationRunDto get(@RestPath Long runId) {
        return taskService.run(runId).orElseThrow(NotFoundException::new);
    }
}
