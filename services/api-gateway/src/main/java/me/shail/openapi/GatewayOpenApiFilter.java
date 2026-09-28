package me.shail.openapi;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.security.OAuthFlow;
import org.eclipse.microprofile.openapi.models.security.SecurityScheme;

/** Adds the same Keycloak login to the gateway's own spec as {@link OpenApiResource} adds to the services'. */
public class GatewayOpenApiFilter implements OASFilter {

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        String realm = ConfigProvider.getConfig().getValue("gateway.keycloak.public-realm-url", String.class);
        OAuthFlow flow = OASFactory.createOAuthFlow()
                .authorizationUrl(realm + "/protocol/openid-connect/auth")
                .tokenUrl(realm + "/protocol/openid-connect/token")
                .scopes(java.util.Map.of("openid", "OpenID Connect"));
        SecurityScheme scheme = OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("Keycloak login. What you can call depends on your role: admin, scheduler or clinical-team.")
                .flows(OASFactory.createOAuthFlows().authorizationCode(flow));
        if (openAPI.getComponents() == null) {
            openAPI.setComponents(OASFactory.createComponents());
        }
        openAPI.getComponents().addSecurityScheme(OpenApiSecurity.SCHEME, scheme);
        openAPI.addSecurityRequirement(OASFactory.createSecurityRequirement().addScheme(OpenApiSecurity.SCHEME, "openid"));
    }
}
