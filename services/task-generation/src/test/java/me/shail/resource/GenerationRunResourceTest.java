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
import java.time.OffsetDateTime;
import java.util.Optional;
import me.shail.dto.GenerationRunDto;
import me.shail.model.GenerationRun;
import me.shail.service.GenerationInProgressException;
import me.shail.service.TaskGenerationService;
import me.shail.service.TaskService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GenerationRunResourceTest {

    @InjectMock
    TaskGenerationService taskGenerationService;

    @InjectMock
    TaskService taskService;

    @Test
    void postReconcilesInTheBackground() {
        GenerationRun run = new GenerationRun();
        run.id = 4L;
        run.trigger = GenerationRun.Trigger.MANUAL;
        run.evaluationRunId = 12L;
        run.status = GenerationRun.Status.RUNNING;
        when(taskGenerationService.start(GenerationRun.Trigger.MANUAL)).thenReturn(Optional.of(run));

        given().when().post("/api/v1/generation-runs")
                .then().statusCode(202).body("runId", is(4)).body("evaluationRunId", is(12)).body("status", is("RUNNING"));
        verify(taskGenerationService, timeout(5000)).execute(run);
    }

    @Test
    void postIs404WithoutAnEvaluationAnd409WhileRunning() {
        when(taskGenerationService.start(GenerationRun.Trigger.MANUAL)).thenReturn(Optional.empty());
        given().when().post("/api/v1/generation-runs")
                .then().statusCode(404).body("message", is("The rules-engine has no successful evaluation yet"));

        when(taskGenerationService.start(GenerationRun.Trigger.MANUAL)).thenThrow(new GenerationInProgressException());
        given().when().post("/api/v1/generation-runs")
                .then().statusCode(409).body("message", is("A task generation run is already running"));
        verify(taskGenerationService, after(200).never()).execute(any());
    }

    @Test
    void latestAndGet() {
        GenerationRunDto dto = new GenerationRunDto(4L, GenerationRun.Trigger.SCHEDULED, 12L, GenerationRun.Status.SUCCEEDED,
                120, 5, 2, 3, null, OffsetDateTime.parse("2026-09-28T06:05:00Z"), OffsetDateTime.parse("2026-09-28T06:05:02Z"));
        when(taskService.latestRun()).thenReturn(Optional.of(dto));
        when(taskService.run(4L)).thenReturn(Optional.of(dto));
        when(taskService.run(5L)).thenReturn(Optional.empty());

        given().when().get("/api/v1/generation-runs/latest").then().statusCode(200).body("tasksCreated", is(5));
        given().when().get("/api/v1/generation-runs/4").then().statusCode(200).body("tasksClosed", is(3));
        given().when().get("/api/v1/generation-runs/5").then().statusCode(404);
    }
}
