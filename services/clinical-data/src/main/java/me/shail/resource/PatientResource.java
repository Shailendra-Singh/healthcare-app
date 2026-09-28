package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.PatientDto;
import me.shail.service.PatientService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/patients")
@Tag(name = "Patients")
public class PatientResource {

    @Inject
    PatientService patientService;

    /** Patients ordered by id; {@code page} starts at 1. */
    @GET
    public Uni<List<PatientDto>> list(
            @RestQuery @DefaultValue("1") @Min(1) long page,
            @RestQuery @DefaultValue("20") @Min(1) @Max(100) int size) {
        return patientService.findPage(page, size);
    }

    @GET
    @Path("/{id}")
    public Uni<PatientDto> get(@RestPath Long id) {
        return patientService.findById(id).onItem().ifNull().failWith(NotFoundException::new);
    }

    /** Looks up by patient_id from patients.csv. */
    @GET
    @Path("/source/{sourcePatientId}")
    public Uni<PatientDto> getBySourceId(@RestPath String sourcePatientId) {
        return patientService.findBySourcePatientId(sourcePatientId).onItem().ifNull().failWith(NotFoundException::new);
    }
}
