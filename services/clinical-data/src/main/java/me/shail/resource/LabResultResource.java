package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.LabResultDto;
import me.shail.service.LabResultService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/patients/{patientId}/lab-results")
@Tag(name = "Lab results")
public class LabResultResource {

    @Inject
    LabResultService labResultService;

    /** Newest result first. */
    @GET
    public Uni<List<LabResultDto>> list(@RestPath Long patientId) {
        return labResultService.findByPatient(patientId);
    }

    /** Most recent result of a test, e.g. {@code ?test=HbA1c} (case-insensitive). */
    @GET
    @Path("/latest")
    public Uni<LabResultDto> latest(@RestPath Long patientId, @RestQuery @NotBlank String test) {
        return labResultService.findLatest(patientId, test).onItem().ifNull().failWith(NotFoundException::new);
    }

    @GET
    @Path("/{id}")
    public Uni<LabResultDto> get(@RestPath Long patientId, @RestPath Long id) {
        return labResultService.findById(id)
                .map(dto -> dto != null && dto.patientId().equals(patientId) ? dto : null)
                .onItem().ifNull().failWith(NotFoundException::new);
    }
}
