package me.shail.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** The Keycloak login added to every spec, so Swagger UI's Authorize button signs in through Keycloak. */
public final class OpenApiSecurity {

    public static final String SCHEME = "keycloak";

    private OpenApiSecurity() {
    }

    /** An OAuth2 authorization-code scheme against the realm as browsers reach it. */
    public static ObjectNode scheme(ObjectMapper json, String publicRealmUrl) {
        ObjectNode flow = json.createObjectNode()
                .put("authorizationUrl", publicRealmUrl + "/protocol/openid-connect/auth")
                .put("tokenUrl", publicRealmUrl + "/protocol/openid-connect/token");
        flow.putObject("scopes").put("openid", "OpenID Connect");
        ObjectNode scheme = json.createObjectNode()
                .put("type", "oauth2")
                .put("description", "Keycloak login. What you can call depends on your role: admin, scheduler or clinical-team.");
        scheme.putObject("flows").set("authorizationCode", flow);
        return scheme;
    }
}
