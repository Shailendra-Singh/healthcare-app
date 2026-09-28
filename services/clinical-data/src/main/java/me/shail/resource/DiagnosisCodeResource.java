package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.DiagnosisCodeDto;
import me.shail.service.DiagnosisCodeService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/diagnosis-codes")
@Tag(name = "Diagnosis codes")
public class DiagnosisCodeResource {

    @Inject
    DiagnosisCodeService diagnosisCodeService;

    /** All codes, or only those in a chronic condition family with {@code chronic=true}. */
    @GET
    public Uni<List<DiagnosisCodeDto>> list(@RestQuery boolean chronic) {
        return chronic ? diagnosisCodeService.findChronic() : diagnosisCodeService.findAll();
    }

    @GET
    @Path("/{icdCode}")
    public Uni<DiagnosisCodeDto> get(@RestPath String icdCode) {
        return diagnosisCodeService.findById(icdCode).onItem().ifNull().failWith(NotFoundException::new);
    }
}
