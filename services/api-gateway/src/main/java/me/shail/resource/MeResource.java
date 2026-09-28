package me.shail.resource;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import me.shail.security.AccessPolicy;
import me.shail.security.Roles;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/me")
@Authenticated
@Tag(name = "Gateway")
public class MeResource {

    /**
     * @param roles     the caller's app roles (admin, scheduler, clinical-team)
     * @param taskTypes task types the caller sees; ["ALL"] for admin
     */
    public record Me(String username, Set<String> roles, List<String> taskTypes) {
    }

    @Inject
    SecurityIdentity identity;

    @Inject
    AccessPolicy accessPolicy;

    @GET
    @Operation(summary = "Who am I", description = "The logged-in user, their app roles and the task types they see. "
            + "A frontend uses this to decide what to show.")
    public Me me() {
        Set<String> roles = new TreeSet<>(identity.getRoles());
        roles.retainAll(Set.of(Roles.ADMIN, Roles.SCHEDULER, Roles.CLINICAL_TEAM));
        List<String> taskTypes = accessPolicy.visibleTaskTypes(identity.getRoles())
                .map(types -> List.copyOf(new TreeSet<>(types)))
                .orElse(List.of("ALL"));
        return new Me(identity.getPrincipal().getName(), roles, taskTypes);
    }
}
