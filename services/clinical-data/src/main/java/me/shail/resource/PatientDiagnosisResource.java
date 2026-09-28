package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.PatientDiagnosisDto;
import me.shail.service.PatientDiagnosisService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

@Path("/patients/{patientId}/diagnoses")
@Tag(name = "Patient diagnoses")
public class PatientDiagnosisResource {

    @Inject
    PatientDiagnosisService patientDiagnosisService;

    /** Newest diagnosis first. */
    @GET
    public Uni<List<PatientDiagnosisDto>> list(@RestPath Long patientId) {
        return patientDiagnosisService.findByPatient(patientId);
    }

    @GET
    @Path("/{id}")
    public Uni<PatientDiagnosisDto> get(@RestPath Long patientId, @RestPath Long id) {
        return patientDiagnosisService.findById(id)
                .map(dto -> dto != null && dto.patientId().equals(patientId) ? dto : null)
                .onItem().ifNull().failWith(NotFoundException::new);
    }
}
