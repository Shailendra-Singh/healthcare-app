package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import me.shail.dto.EtlHeartbeatDto;
import me.shail.service.EtlHeartbeatService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/etl-heartbeat")
@Tag(name = "ETL")
public class EtlHeartbeatResource {

    @Inject
    EtlHeartbeatService etlHeartbeatService;

    /** 404 until the ETL has checked the data folder once. */
    @GET
    @Operation(summary = "Last ETL check",
            description = "When the ETL last checked the data folder and what it found, including checks where the "
                    + "files were unchanged. `stale` turns true once two checks in a row are overdue.")
    public Uni<EtlHeartbeatDto> get() {
        return etlHeartbeatService.find().onItem().ifNull().failWith(NotFoundException::new);
    }
}
