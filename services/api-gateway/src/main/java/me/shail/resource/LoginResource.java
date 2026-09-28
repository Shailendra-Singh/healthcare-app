package me.shail.resource;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * The frontend's "Sign in" button. Reaching it without a session starts the Keycloak login (the password is typed
 * on Keycloak's page, never in the app); once signed in, it sends the browser back to the app.
 */
@Path("/login")
@Authenticated
public class LoginResource {

    @GET
    @Operation(hidden = true)
    public Response login() {
        return Response.seeOther(URI.create("/")).build();
    }
}
