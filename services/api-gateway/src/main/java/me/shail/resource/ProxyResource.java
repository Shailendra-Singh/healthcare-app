package me.shail.resource;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import me.shail.proxy.DownstreamClient;
import me.shail.proxy.DownstreamResponse;
import me.shail.proxy.TaskRoutes;
import me.shail.security.AccessPolicy;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * Routes {@code /{service}/api/**} to that service, after checking the caller's roles. Each service's own API is
 * documented in the Swagger UI dropdown; these catch-all methods are hidden from the gateway's own spec.
 */
@Path("/{service:clinical-data|rules-engine|task-generation}/{path:api/.+}")
@Authenticated
public class ProxyResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    AccessPolicy accessPolicy;

    @Inject
    TaskRoutes taskRoutes;

    @Inject
    DownstreamClient downstream;

    @Context
    UriInfo uriInfo;

    @Context
    HttpHeaders headers;

    @GET
    @Operation(hidden = true)
    public Response get(String service, String path) {
        return route("GET", service, path, null);
    }

    @POST
    @Operation(hidden = true)
    public Response post(String service, String path, byte[] body) {
        return route("POST", service, path, body);
    }

    @PUT
    @Operation(hidden = true)
    public Response put(String service, String path, byte[] body) {
        return route("PUT", service, path, body);
    }

    @PATCH
    @Operation(hidden = true)
    public Response patch(String service, String path, byte[] body) {
        return route("PATCH", service, path, body);
    }

    @DELETE
    @Operation(hidden = true)
    public Response delete(String service, String path) {
        return route("DELETE", service, path, null);
    }

    private Response route(String method, String service, String rawPath, byte[] requestBody) {
        String path = "/" + rawPath;
        byte[] body = requestBody == null || requestBody.length == 0 ? null : requestBody;
        if (!accessPolicy.allows(service, method, path, identity.getRoles())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorMappers.Error("Your role does not allow " + method + " " + path + " on " + service))
                    .build();
        }
        String query = uriInfo.getRequestUri().getRawQuery();
        // A content type only describes a body; clients send one on empty requests too
        String contentType = body == null ? null : headers.getHeaderString(HttpHeaders.CONTENT_TYPE);
        DownstreamResponse response = service.equals("task-generation")
                ? taskRoutes.handle(method, path, query, contentType, body, identity.getPrincipal().getName(),
                        accessPolicy.visibleTaskTypes(identity.getRoles()))
                        .orElseGet(() -> downstream.send(service, method, path, query, contentType, body))
                : downstream.send(service, method, path, query, contentType, body);
        Response.ResponseBuilder builder = Response.status(response.status());
        if (response.body() != null && response.body().length > 0) {
            builder.entity(response.body());
        }
        if (response.contentType() != null) {
            builder.type(response.contentType());
        }
        return builder.build();
    }
}
