package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import java.nio.charset.StandardCharsets;
import me.shail.proxy.DownstreamClient;
import me.shail.proxy.DownstreamResponse;
import me.shail.proxy.DownstreamUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@QuarkusTest
class ProxyResourceTest {

    static final String SCHEDULING = "{\"taskId\":1,\"taskType\":\"SCHEDULING\",\"specialty\":\"PCP\"}";
    static final String REFERRAL = "{\"taskId\":2,\"taskType\":\"REFERRAL\",\"specialty\":\"Podiatry\"}";

    @InjectMock
    DownstreamClient downstream;

    @Test
    void anonymousCallsAreRejected() {
        given().when().get("/task-generation/api/v1/tasks").then().statusCode(401);
        verify(downstream, never()).send(anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    @TestSecurity(user = "ada", roles = "admin")
    void adminCallsAreForwardedAsIs() {
        when(downstream.send("rules-engine", "POST", "/api/v1/evaluations", null, null, null))
                .thenReturn(DownstreamResponse.json(202, "{\"runId\":9,\"status\":\"RUNNING\"}"));
        when(downstream.send("clinical-data", "GET", "/api/v1/patients", "page=2&size=5", null, null))
                .thenReturn(DownstreamResponse.json(200, "[{\"id\":6}]"));

        given().when().post("/rules-engine/api/v1/evaluations").then().statusCode(202).body("runId", is(9));
        given().queryParam("page", 2).queryParam("size", 5).when().get("/clinical-data/api/v1/patients")
                .then().statusCode(200).body("[0].id", is(6));
    }

    @Test
    @TestSecurity(user = "ada", roles = "admin")
    void adminCanAskTheEtlToCheckNow() {
        when(downstream.send(eq("etl"), eq("POST"), eq("/api/v1/runs"), isNull(), eq("application/json"), any()))
                .thenReturn(DownstreamResponse.json(202, "{\"force\":true}"));

        given().contentType("application/json").body("{\"force\":true}").when().post("/etl/api/v1/runs")
                .then().statusCode(202).body("force", is(true));
        verify(downstream).send(eq("etl"), eq("POST"), eq("/api/v1/runs"), isNull(), eq("application/json"),
                argThat(body -> new String(body, StandardCharsets.UTF_8).equals("{\"force\":true}")));
    }

    @Test
    @TestSecurity(user = "cleo", roles = "clinical-team")
    void onlyAdminsRunTheEtlOrSeeItsHistory() {
        given().when().post("/etl/api/v1/runs").then().statusCode(403);
        given().when().get("/clinical-data/api/v1/etl-runs").then().statusCode(403);
        given().when().post("/rules-engine/api/v1/evaluations").then().statusCode(403);
        verify(downstream, never()).send(anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    @TestSecurity(user = "ada", roles = "admin")
    void adminSeesEveryTaskTypeWithoutFiltering() {
        when(downstream.send("task-generation", "GET", "/api/v1/tasks", null, null, null))
                .thenReturn(DownstreamResponse.json(200, "[" + SCHEDULING + "," + REFERRAL + "]"));

        given().when().get("/task-generation/api/v1/tasks").then().statusCode(200).body("taskType", contains("SCHEDULING", "REFERRAL"));
    }

    @Test
    @TestSecurity(user = "sam", roles = "scheduler")
    void schedulerListsOnlySchedulingTasks() {
        when(downstream.send("task-generation", "GET", "/api/v1/tasks", "status=ACTIVE&taskType=SCHEDULING", null, null))
                .thenReturn(DownstreamResponse.json(200, "[" + SCHEDULING + "]"));

        given().queryParam("status", "ACTIVE").when().get("/task-generation/api/v1/tasks")
                .then().statusCode(200).body("taskType", contains("SCHEDULING"));
    }

    @Test
    @TestSecurity(user = "sam", roles = "scheduler")
    void schedulerAskingForReferralsGetsNone() {
        given().queryParam("taskType", "REFERRAL").when().get("/task-generation/api/v1/tasks")
                .then().statusCode(200).body("$", empty());
        verify(downstream, never()).send(eq("task-generation"), anyString(), anyString(), any(), any(), any());
    }

    @Test
    @TestSecurity(user = "cleo", roles = "clinical-team")
    void clinicalTeamListsSchedulingAndReferralTasks() {
        when(downstream.send("task-generation", "GET", "/api/v1/tasks", "taskType=REFERRAL&taskType=SCHEDULING", null, null))
                .thenReturn(DownstreamResponse.json(200, "[" + SCHEDULING + "," + REFERRAL + "]"));

        given().when().get("/task-generation/api/v1/tasks").then().statusCode(200).body("taskType", contains("SCHEDULING", "REFERRAL"));
    }

    @Test
    @TestSecurity(user = "sam", roles = "scheduler")
    void aReferralLooksMissingToAScheduler() {
        when(downstream.send("task-generation", "GET", "/api/v1/tasks/2", null, null, null)).thenReturn(DownstreamResponse.json(200, REFERRAL));
        when(downstream.send("task-generation", "GET", "/api/v1/tasks/1", null, null, null)).thenReturn(DownstreamResponse.json(200, SCHEDULING));

        given().when().get("/task-generation/api/v1/tasks/2").then().statusCode(404);
        given().when().get("/task-generation/api/v1/tasks/1").then().statusCode(200).body("specialty", is("PCP"));
    }

    @Test
    @TestSecurity(user = "sam", roles = "scheduler")
    void aSchedulerCannotChangeAReferral() {
        when(downstream.send("task-generation", "GET", "/api/v1/tasks/2", null, null, null)).thenReturn(DownstreamResponse.json(200, REFERRAL));

        given().contentType("application/json").body("{\"actor\":\"sam\",\"status\":\"CANCELLED\"}")
                .when().patch("/task-generation/api/v1/tasks/2").then().statusCode(404);
        verify(downstream, never()).send(eq("task-generation"), eq("PATCH"), anyString(), any(), any(), any());
    }

    @Test
    @TestSecurity(user = "sam", roles = "scheduler")
    void taskChangesAreRecordedUnderTheLoggedInUser() {
        when(downstream.send("task-generation", "GET", "/api/v1/tasks/1", null, null, null)).thenReturn(DownstreamResponse.json(200, SCHEDULING));
        when(downstream.send(eq("task-generation"), eq("PATCH"), eq("/api/v1/tasks/1"), isNull(), eq("application/json"), any()))
                .thenReturn(DownstreamResponse.json(200, "{\"taskId\":1,\"status\":\"IN_PROGRESS\"}"));

        given().contentType("application/json").body("{\"actor\":\"someone.else\",\"status\":\"IN_PROGRESS\"}")
                .when().patch("/task-generation/api/v1/tasks/1").then().statusCode(200).body("status", is("IN_PROGRESS"));

        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(downstream).send(eq("task-generation"), eq("PATCH"), eq("/api/v1/tasks/1"), isNull(), eq("application/json"), body.capture());
        assertEquals("{\"actor\":\"sam\",\"status\":\"IN_PROGRESS\"}", new String(body.getValue(), StandardCharsets.UTF_8));
    }

    @Test
    @TestSecurity(user = "sam", roles = "scheduler")
    void aPatientsTasksAreFilteredToTheRolesTypes() {
        when(downstream.send("task-generation", "GET", "/api/v1/patients/P1/tasks", null, null, null))
                .thenReturn(DownstreamResponse.json(200, "[" + SCHEDULING + "," + REFERRAL + "]"));

        given().when().get("/task-generation/api/v1/patients/P1/tasks").then().statusCode(200).body("taskType", contains("SCHEDULING"));
    }

    @Test
    @TestSecurity(user = "sam", roles = "scheduler")
    void forbiddenRoutesAre403WithAMessage() {
        given().when().get("/clinical-data/api/v1/patients/7/diagnoses")
                .then().statusCode(403).body("message", is("Your role does not allow GET /api/v1/patients/7/diagnoses on clinical-data"));
        given().when().post("/task-generation/api/v1/generation-runs").then().statusCode(403);
        verify(downstream, never()).send(anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    @TestSecurity(user = "ada", roles = "admin")
    void onlyTheServicesApisAreRouted() {
        given().when().get("/clinical-data/q/dev-ui").then().statusCode(404);
        given().when().get("/billing/api/v1/invoices").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "ada", roles = "admin")
    void anUnreachableServiceIs502() {
        when(downstream.send("rules-engine", "GET", "/api/v1/programs", null, null, null))
                .thenThrow(new DownstreamUnavailableException("rules-engine", new java.net.ConnectException("Connection refused")));

        given().when().get("/rules-engine/api/v1/programs").then().statusCode(502).body("message", is("rules-engine is unavailable"));
    }

    @Test
    @TestSecurity(user = "ada", roles = "admin")
    void downstreamErrorsPassThrough() {
        when(downstream.send("task-generation", "PATCH", "/api/v1/tasks/5", null, "application/json",
                "{\"status\":\"OPEN\",\"actor\":\"ada\"}".getBytes(StandardCharsets.UTF_8)))
                .thenReturn(DownstreamResponse.json(409, "{\"message\":\"Task 5 is CANCELLED and can no longer change\"}"));

        given().contentType("application/json").body("{\"status\":\"OPEN\"}").when().patch("/task-generation/api/v1/tasks/5")
                .then().statusCode(409).body("message", is("Task 5 is CANCELLED and can no longer change"));
    }
}
