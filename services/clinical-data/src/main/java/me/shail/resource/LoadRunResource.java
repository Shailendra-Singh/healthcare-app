package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import me.shail.dto.LoadRunDto;
import me.shail.service.LoadRunService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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
                    + "rows loaded and rows rejected. Cycles that found the files unchanged are not recorded.")
    public Uni<LoadRunDto> latest() {
        return loadRunService.findLatest().onItem().ifNull().failWith(NotFoundException::new);
    }
}
