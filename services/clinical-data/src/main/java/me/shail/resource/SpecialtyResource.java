package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.SpecialtyDto;
import me.shail.service.SpecialtyService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/specialties")
@Tag(name = "Specialties")
public class SpecialtyResource {

    @Inject
    SpecialtyService specialtyService;

    /** All, sorted by name; with {@code name}, the case-insensitive match (zero or one). */
    @GET
    public Uni<List<SpecialtyDto>> list(@RestQuery String name) {
        if (name == null || name.isBlank()) {
            return specialtyService.findAll();
        }
        return specialtyService.findByName(name).map(dto -> dto == null ? List.of() : List.of(dto));
    }

    @GET
    @Path("/{id}")
    public Uni<SpecialtyDto> get(@RestPath Short id) {
        return specialtyService.findById(id).onItem().ifNull().failWith(NotFoundException::new);
    }
}
