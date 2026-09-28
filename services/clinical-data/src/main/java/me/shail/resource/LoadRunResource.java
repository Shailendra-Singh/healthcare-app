package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import me.shail.dto.LoadRunDto;
import me.shail.service.LoadRunService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/etl-runs")
@Tag(name = "ETL")
public class LoadRunResource {

    @Inject
    LoadRunService loadRunService;

    /** 404 until the ETL has loaded files at least once. */
    @GET
    @Path("/latest")
    @Operation(summary = "Latest ETL run",
            description = "Status, timing and error of the most recent CSV load, with each file's checksum, "
                    + "rows loaded and rows rejected. Cycles that found the files unchanged are not recorded. "
                    + "`status=SUCCEEDED` returns the latest successful load instead (the rules-engine uses it to "
                    + "re-evaluate after new data).")
    public Uni<LoadRunDto> latest(
            @RestQuery @Pattern(regexp = "SUCCEEDED", message = "only SUCCEEDED is supported") String status) {
        return (status == null ? loadRunService.findLatest() : loadRunService.findLatest(status))
                .onItem().ifNull().failWith(NotFoundException::new);
    }
}
