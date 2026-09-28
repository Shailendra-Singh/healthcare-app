package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.EncounterDto;
import me.shail.service.EncounterService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

@Path("/patients/{patientId}/encounters")
@Tag(name = "Encounters")
public class EncounterResource {

    @Inject
    EncounterService encounterService;

    /** Past and scheduled encounters, newest first. */
    @GET
    public Uni<List<EncounterDto>> list(@RestPath Long patientId) {
        return encounterService.findByPatient(patientId);
    }

    /** Scheduled appointments from today on, soonest first. */
    @GET
    @Path("/upcoming")
    public Uni<List<EncounterDto>> upcoming(@RestPath Long patientId) {
        return encounterService.findUpcoming(patientId);
    }

    @GET
    @Path("/{id}")
    public Uni<EncounterDto> get(@RestPath Long patientId, @RestPath Long id) {
        return encounterService.findById(id)
                .map(dto -> dto != null && dto.patientId().equals(patientId) ? dto : null)
                .onItem().ifNull().failWith(NotFoundException::new);
    }
}
