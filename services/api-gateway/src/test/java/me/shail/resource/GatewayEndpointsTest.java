package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import me.shail.proxy.DownstreamClient;
import me.shail.proxy.DownstreamResponse;
import org.junit.jupiter.api.Test;

/** /api/v1/me, the rewritten service specs and the Swagger UI dropdown. */
@QuarkusTest
class GatewayEndpointsTest {

    @InjectMock
    DownstreamClient downstream;

    @Test
    @TestSecurity(user = "cleo", roles = {"clinical-team", "offline_access"})
    void meShowsTheAppRolesAndTaskTypes() {
        given().when().get("/api/v1/me")
                .then().statusCode(200)
                .body("username", is("cleo"))
                .body("roles", contains("clinical-team"))
                .body("taskTypes", contains("REFERRAL", "SCHEDULING"));
    }

    @Test
    @TestSecurity(user = "ada", roles = "admin")
    void adminSeesAllTaskTypes() {
        given().when().get("/api/v1/me").then().statusCode(200).body("taskTypes", contains("ALL"));
    }

    @Test
    void serviceSpecsArePublicAndGoThroughTheGatewayWithKeycloakLogin() {
        when(downstream.send("task-generation", "GET", "/q/openapi", "format=json", null, null)).thenReturn(DownstreamResponse.json(200,
                "{\"openapi\":\"3.1.0\",\"servers\":[{\"url\":\"http://task-generation:8080\"}],\"paths\":{\"/api/v1/tasks\":{}}}"));

        given().when().get("/openapi/task-generation")
                .then().statusCode(200)
                .body("servers[0].url", is("/task-generation"))
                .body("paths.'/api/v1/tasks'", notNullValue())
                .body("components.securitySchemes.keycloak.type", is("oauth2"))
                .body("components.securitySchemes.keycloak.flows.authorizationCode.authorizationUrl",
                        is("http://localhost:8180/realms/healthcare/protocol/openid-connect/auth"))
                .body("security[0].keycloak[0]", is("openid"));
    }

    @Test
    void theGatewaysOwnSpecHasTheLoginAndHidesTheRoutes() {
        given().queryParam("format", "json").when().get("/q/openapi")
                .then().statusCode(200)
                .body("components.securitySchemes.keycloak.flows.authorizationCode.tokenUrl",
                        is("http://localhost:8180/realms/healthcare/protocol/openid-connect/token"))
                .body("paths.keySet()", contains("/api/v1/me"));
    }

    @Test
    void swaggerUiOffersADropdownOfEveryService() {
        given().when().get("/q/swagger-ui/index.html")
                .then().statusCode(200)
                .body(containsString("/openapi/clinical-data"))
                .body(containsString("/openapi/rules-engine"))
                .body(containsString("/openapi/task-generation"))
                .body(containsString("api-gateway-swagger"));
    }
}
