package me.shail.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.PatientProgramDto;
import me.shail.service.ResultsService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

@Path("/patients/{sourcePatientId}/programs")
@Tag(name = "Patients")
public class PatientProgramResource {

    @Inject
    ResultsService resultsService;

    @GET
    @Operation(summary = "A patient's programs, tiers and care needs",
            description = "By patient_id from patients.csv, from the latest successful evaluation. Empty when the "
                    + "patient is in no program (or unknown).")
    public List<PatientProgramDto> list(@RestPath String sourcePatientId) {
        return resultsService.patientPrograms(sourcePatientId);
    }
}
