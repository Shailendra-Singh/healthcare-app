package me.shail.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import me.shail.dto.ProgramCatalogDto;
import me.shail.dto.ProgramDto;
import me.shail.rules.ProgramCatalog;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

@Path("/programs")
@Tag(name = "Programs")
public class ProgramResource {

    @Inject
    ProgramCatalog catalog;

    @GET
    @Operation(summary = "Care programs in care-programs/",
            description = "Read from the folder on every call, so this shows what the next evaluation will use, "
                    + "including files that fail validation and why.")
    public ProgramCatalogDto list() {
        return ProgramCatalogDto.from(catalog.load());
    }

    @GET
    @Path("/{id}")
    public ProgramDto get(@RestPath String id) {
        return catalog.load().programs().stream()
                .filter(p -> p.definition().id().equals(id))
                .findFirst()
                .map(ProgramDto::from)
                .orElseThrow(NotFoundException::new);
    }
}
