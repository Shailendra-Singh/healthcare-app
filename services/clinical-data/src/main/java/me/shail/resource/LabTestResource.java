package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.LabTestDto;
import me.shail.service.LabTestService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/lab-tests")
@Tag(name = "Lab tests")
public class LabTestResource {

    @Inject
    LabTestService labTestService;

    /** All, sorted by name; with {@code name}, the case-insensitive match (zero or one). */
    @GET
    public Uni<List<LabTestDto>> list(@RestQuery String name) {
        if (name == null || name.isBlank()) {
            return labTestService.findAll();
        }
        return labTestService.findByName(name).map(dto -> dto == null ? List.of() : List.of(dto));
    }

    @GET
    @Path("/{id}")
    public Uni<LabTestDto> get(@RestPath Short id) {
        return labTestService.findById(id).onItem().ifNull().failWith(NotFoundException::new);
    }
}
