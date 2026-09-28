package me.shail.config;

import io.smallrye.config.ConfigMapping;
import java.net.URI;
import java.util.List;
import java.util.Map;

/** {@code gateway.*} in application.properties. */
@ConfigMapping(prefix = "gateway")
public interface GatewayConfig {

    /** The downstream services by route name: {@code /clinical-data/**} goes to {@code services.clinical-data.url}. */
    Map<String, Service> services();

    /**
     * Task types each role may see and change (e.g. {@code scheduler=SCHEDULING}). {@code admin} sees every type and
     * needs no entry; a role without an entry sees no tasks.
     */
    Map<String, List<String>> taskAccess();

    Keycloak keycloak();

    interface Service {
        URI url();
    }

    interface Keycloak {
        /** The realm as browsers reach it, e.g. http://localhost:8180/realms/healthcare (Swagger UI's login). */
        String publicRealmUrl();
    }
}
