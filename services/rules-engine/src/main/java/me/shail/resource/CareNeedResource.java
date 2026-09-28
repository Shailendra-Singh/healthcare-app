package me.shail.resource;

import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.CareNeedDto;
import me.shail.rules.ProgramEvaluator.NeedStatus;
import me.shail.service.ResultsService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/care-needs")
@Tag(name = "Care needs")
public class CareNeedResource {

    @Inject
    ResultsService resultsService;

    @GET
    @Operation(summary = "Care gap list",
            description = "Care needs from the latest successful evaluation, earliest due date first. Every filter "
                    + "is optional; e.g. status=OVERDUE&programId=diabetes-management. `page` starts at 1.")
    public List<CareNeedDto> list(
            @RestQuery String programId,
            @RestQuery String tierId,
            @RestQuery @Pattern(regexp = "MET|SCHEDULED|OVERDUE", message = "must be MET, SCHEDULED or OVERDUE") String status,
            @RestQuery String specialty,
            @RestQuery @DefaultValue("1") @Min(1) int page,
            @RestQuery @DefaultValue("100") @Min(1) @Max(1000) int size) {
        return resultsService.careNeeds(programId, tierId, status == null ? null : NeedStatus.valueOf(status),
                specialty, page, size);
    }
}
