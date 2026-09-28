package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.time.LocalDate;
import java.util.List;
import me.shail.dto.EvaluationInputDto;
import me.shail.service.EvaluationInputService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/evaluation-inputs")
@Tag(name = "Rules engine")
public class EvaluationInputResource {

    @Inject
    EvaluationInputService evaluationInputService;

    @GET
    @Operation(summary = "Patients with their evaluation facts",
            description = "One page of patients, ordered by id, each with diagnoses (and condition groups), the latest "
                    + "result per lab test, and per specialty the last visit on or before `asOf` and the next "
                    + "appointment after it. `page` starts at 1; `asOf` defaults to today.")
    public Uni<List<EvaluationInputDto>> list(
            @RestQuery @DefaultValue("1") @Min(1) long page,
            @RestQuery @DefaultValue("500") @Min(1) @Max(1000) int size,
            @RestQuery LocalDate asOf) {
        return evaluationInputService.findPage(page, size, asOf == null ? LocalDate.now() : asOf);
    }
}
