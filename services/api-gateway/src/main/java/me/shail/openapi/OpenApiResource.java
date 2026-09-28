package me.shail.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import me.shail.config.GatewayConfig;
import me.shail.proxy.DownstreamClient;
import me.shail.proxy.DownstreamResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * Each service's OpenAPI spec for the Swagger UI dropdown, rewritten to go through the gateway: the server becomes
 * {@code /{service}} and every operation requires the Keycloak login. The docs are public; the calls are not.
 */
@Path("/openapi/{service:clinical-data|rules-engine|task-generation}")
@PermitAll
public class OpenApiResource {

    @Inject
    DownstreamClient downstream;

    @Inject
    ObjectMapper json;

    @Inject
    GatewayConfig config;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    public JsonNode spec(String service) throws IOException {
        DownstreamResponse response = downstream.send(service, "GET", "/q/openapi", "format=json", null, null);
        if (!response.ok()) {
            throw new NotFoundException();
        }
        ObjectNode spec = (ObjectNode) json.readTree(response.body());
        spec.putArray("servers").addObject()
                .put("url", "/" + service)
                .put("description", "Through the api-gateway (role-based access)");
        spec.with("components").with("securitySchemes")
                .set(OpenApiSecurity.SCHEME, OpenApiSecurity.scheme(json, config.keycloak().publicRealmUrl()));
        spec.putArray("security").addObject().putArray(OpenApiSecurity.SCHEME).add("openid");
        return spec;
    }
}
