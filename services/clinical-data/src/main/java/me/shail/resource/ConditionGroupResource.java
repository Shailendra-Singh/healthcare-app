package me.shail.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import java.util.List;
import me.shail.dto.ConditionGroupDto;
import me.shail.service.ConditionGroupService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/condition-groups")
@Tag(name = "Condition groups")
public class ConditionGroupResource {

    @Inject
    ConditionGroupService conditionGroupService;

    /** All, sorted by ICD prefix; with {@code icdPrefix}, the exact match (zero or one). */
    @GET
    public Uni<List<ConditionGroupDto>> list(@RestQuery String icdPrefix) {
        if (icdPrefix == null || icdPrefix.isBlank()) {
            return conditionGroupService.findAll();
        }
        return conditionGroupService.findByIcdPrefix(icdPrefix).map(dto -> dto == null ? List.of() : List.of(dto));
    }

    @GET
    @Path("/{id}")
    public Uni<ConditionGroupDto> get(@RestPath Short id) {
        return conditionGroupService.findById(id).onItem().ifNull().failWith(NotFoundException::new);
    }
}
