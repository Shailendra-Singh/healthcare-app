package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.LanguageDto;
import me.shail.service.LanguageService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/languages")
@Tag(name = "Languages")
public class LanguageResource {

    @Inject
    LanguageService languageService;

    /** All, sorted by name; with {@code name}, the case-insensitive match (zero or one). */
    @GET
    public Uni<List<LanguageDto>> list(@RestQuery String name) {
        if (name == null || name.isBlank()) {
            return languageService.findAll();
        }
        return languageService.findByName(name).map(dto -> dto == null ? List.of() : List.of(dto));
    }

    @GET
    @Path("/{id}")
    public Uni<LanguageDto> get(@RestPath Short id) {
        return languageService.findById(id).onItem().ifNull().failWith(NotFoundException::new);
    }
}
