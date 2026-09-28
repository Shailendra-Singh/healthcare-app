package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.ProviderDto;
import me.shail.service.ProviderService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/providers")
@Tag(name = "Providers")
public class ProviderResource {

    @Inject
    ProviderService providerService;

    /** All, sorted by name; with {@code name}, the case-insensitive match (zero or one). */
    @GET
    public Uni<List<ProviderDto>> list(@RestQuery String name) {
        if (name == null || name.isBlank()) {
            return providerService.findAll();
        }
        return providerService.findByName(name).map(dto -> dto == null ? List.of() : List.of(dto));
    }

    @GET
    @Path("/{id}")
    public Uni<ProviderDto> get(@RestPath Long id) {
        return providerService.findById(id).onItem().ifNull().failWith(NotFoundException::new);
    }
}
