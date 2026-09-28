package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.shail.dto.CareNeedDto;
import me.shail.dto.EvaluationRunDto;
import me.shail.model.EvaluationRun;
import me.shail.service.EvaluationInProgressException;
import me.shail.service.EvaluationService;
import me.shail.service.ResultsService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EvaluationResourceTest {

    @InjectMock
    EvaluationService evaluationService;

    @InjectMock
    ResultsService resultsService;

    @Test
    void postStartsARunAndEvaluatesInTheBackground() {
        EvaluationRun run = new EvaluationRun();
        run.id = 5L;
        run.trigger = EvaluationRun.Trigger.MANUAL;
        run.status = EvaluationRun.Status.RUNNING;
        run.asOfDate = LocalDate.of(2026, 9, 28);
        when(evaluationService.start(EvaluationRun.Trigger.MANUAL)).thenReturn(run);

        given().when().post("/api/v1/evaluations")
                .then().statusCode(202)
                .body("runId", is(5))
                .body("status", is("RUNNING"))
                .body("trigger", is("MANUAL"));
        verify(evaluationService, timeout(5000)).execute(run);
    }

    @Test
    void postIs409WhileAnotherRunIsRunning() {
        when(evaluationService.start(EvaluationRun.Trigger.MANUAL)).thenThrow(new EvaluationInProgressException());

        given().when().post("/api/v1/evaluations").then().statusCode(409);
        verify(evaluationService, after(200).never()).execute(any());
    }

    @Test
    void latestReturnsTheRunWithItsSummary() {
        OffsetDateTime finished = OffsetDateTime.parse("2026-09-28T06:00:12Z");
        when(resultsService.latestRun()).thenReturn(Optional.of(new EvaluationRunDto(9L, EvaluationRun.Trigger.SCHEDULED,
                LocalDate.of(2026, 9, 28), EvaluationRun.Status.SUCCEEDED, 3L, 120,
                List.of("new.yaml: name: required (skipped)"), null, finished.minusSeconds(12), finished,
                new EvaluationRunDto.Summary(List.of(new EvaluationRunDto.TierCount("diabetes-management", "high-risk", 14)),
                        Map.of("MET", 50L, "SCHEDULED", 7L, "OVERDUE", 33L)))));

        given().when().get("/api/v1/evaluations/latest")
                .then().statusCode(200)
                .body("runId", is(9))
                .body("patientsEvaluated", is(120))
                .body("programErrors[0]", is("new.yaml: name: required (skipped)"))
                .body("summary.tiers[0].patients", is(14))
                .body("summary.needsByStatus.OVERDUE", is(33));
    }

    @Test
    void latestSucceededUsesTheStatusFilter() {
        when(resultsService.latestSucceededRun()).thenReturn(Optional.of(new EvaluationRunDto(8L, EvaluationRun.Trigger.SCHEDULED,
                LocalDate.of(2026, 9, 28), EvaluationRun.Status.SUCCEEDED, 3L, 10, List.of(), null, null, null, null)));

        given().queryParam("status", "SUCCEEDED").when().get("/api/v1/evaluations/latest")
                .then().statusCode(200).body("runId", is(8));
        given().queryParam("status", "FAILED").when().get("/api/v1/evaluations/latest").then().statusCode(400);
    }

    @Test
    void careNeedsOfARunArePagedAnd404WhenTheRunIsNotAvailable() {
        CareNeedDto need = new CareNeedDto("P1", "diabetes-management", "high-risk", "Podiatry", 180, null,
                LocalDate.of(2026, 9, 28), null, me.shail.rules.ProgramEvaluator.NeedStatus.OVERDUE, "normal", null,
                new CareNeedDto.TaskPolicy("scheduling", "referral"));
        when(resultsService.careNeedsOfRun(8L, 2, 50)).thenReturn(Optional.of(List.of(need)));
        when(resultsService.careNeedsOfRun(9L, 1, 500)).thenReturn(Optional.empty());

        given().queryParam("page", 2).queryParam("size", 50).when().get("/api/v1/evaluations/8/care-needs")
                .then().statusCode(200)
                .body("[0].specialty", is("Podiatry"))
                .body("[0].tasks.pastCadence", is("scheduling"));
        given().when().get("/api/v1/evaluations/9/care-needs").then().statusCode(404);
    }

    @Test
    void latestAndUnknownRunAre404() {
        when(resultsService.latestRun()).thenReturn(Optional.empty());
        when(resultsService.run(99L)).thenReturn(Optional.empty());

        given().when().get("/api/v1/evaluations/latest").then().statusCode(404);
        given().when().get("/api/v1/evaluations/99").then().statusCode(404);
    }
}
