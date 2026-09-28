package me.shail.resource;

import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import me.shail.dto.EvaluationRunDto;
import me.shail.model.EvaluationRun;
import me.shail.service.EvaluationInProgressException;
import me.shail.service.EvaluationService;
import me.shail.service.ResultsService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/evaluations")
@Tag(name = "Evaluations")
public class EvaluationResource {

    @Inject
    EvaluationService evaluationService;

    @Inject
    ResultsService resultsService;

    @POST
    @Operation(summary = "Start an evaluation now",
            description = "Returns 202 with the new run (RUNNING) and evaluates in the background; poll "
                    + "GET /evaluations/{runId}. 409 while another evaluation is running.")
    public RestResponse<EvaluationRunDto> start() {
        EvaluationRun run;
        try {
            run = evaluationService.start(EvaluationRun.Trigger.MANUAL);
        } catch (EvaluationInProgressException e) {
            throw new ClientErrorException(e.getMessage(), Response.Status.CONFLICT);
        }
        Infrastructure.getDefaultWorkerPool().execute(() -> evaluationService.execute(run));
        return RestResponse.status(RestResponse.Status.ACCEPTED, EvaluationRunDto.from(run, null));
    }

    @GET
    @Path("/latest")
    @Operation(summary = "Latest evaluation run", description = "With result counts when it SUCCEEDED. 404 before the first run.")
    public EvaluationRunDto latest() {
        return resultsService.latestRun().orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/{runId}")
    public EvaluationRunDto get(@RestPath Long runId) {
        return resultsService.run(runId).orElseThrow(NotFoundException::new);
    }
}
