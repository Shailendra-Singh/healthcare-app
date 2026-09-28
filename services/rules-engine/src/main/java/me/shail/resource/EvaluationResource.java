package me.shail.resource;

import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.List;
import me.shail.dto.CareNeedDto;
import me.shail.dto.EvaluationRunDto;
import me.shail.model.EvaluationRun;
import me.shail.service.EvaluationInProgressException;
import me.shail.service.EvaluationService;
import me.shail.service.ResultsService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
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
    @Operation(summary = "Latest evaluation run",
            description = "With result counts when it SUCCEEDED. `status=SUCCEEDED` returns the latest successful run "
                    + "instead. 404 when there is none.")
    public EvaluationRunDto latest(
            @RestQuery @Pattern(regexp = "SUCCEEDED", message = "only SUCCEEDED is supported") String status) {
        return (status == null ? resultsService.latestRun() : resultsService.latestSucceededRun())
                .orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/{runId}/care-needs")
    @Operation(summary = "All care needs of one successful run",
            description = "Ordered by patient, program and specialty, with each program's task policy; `page` starts at "
                    + "1. Paging stays on this run even while newer runs finish. 404 unless the run SUCCEEDED and is "
                    + "still kept.")
    public List<CareNeedDto> careNeeds(@RestPath Long runId,
            @RestQuery @DefaultValue("1") @Min(1) int page,
            @RestQuery @DefaultValue("500") @Min(1) @Max(1000) int size) {
        return resultsService.careNeedsOfRun(runId, page, size).orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/{runId}")
    public EvaluationRunDto get(@RestPath Long runId) {
        return resultsService.run(runId).orElseThrow(NotFoundException::new);
    }
}
