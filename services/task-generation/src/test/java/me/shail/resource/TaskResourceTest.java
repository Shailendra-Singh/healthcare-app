package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import me.shail.dto.TaskDto;
import me.shail.dto.TaskEventDto;
import me.shail.dto.TaskUpdateRequest;
import me.shail.model.Task;
import me.shail.repository.TaskRepository;
import me.shail.service.InvalidTaskChangeException;
import me.shail.service.TaskService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TaskResourceTest {

    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-28T06:05:00Z");

    @InjectMock
    TaskService taskService;

    static TaskDto referral(Task.Status status, List<TaskEventDto> events) {
        return new TaskDto(7L, "P1", "diabetes-management", "high-risk", "Podiatry", "REFERRAL", status, "normal", 180,
                LocalDate.of(2026, 9, 28), null, null, null, NOW, NOW, null, null, null, events);
    }

    @Test
    void listDefaultsToActiveTasks() {
        when(taskService.search(TaskRepository.ACTIVE, List.of(), null, null, null, 1, 100)).thenReturn(List.of(referral(Task.Status.OPEN, null)));

        given().when().get("/api/v1/tasks")
                .then().statusCode(200)
                .body("[0].taskType", is("REFERRAL"))
                .body("[0].cadenceDays", is(180))
                .body("[0]", not(hasKey("lastVisitDate")))
                .body("[0]", not(hasKey("events")));
    }

    @Test
    void listPassesFiltersAndStatusChoices() {
        when(taskService.search(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        given().queryParam("status", "ALL").queryParam("taskType", "SCHEDULING").queryParam("programId", "diabetes-management")
                .queryParam("specialty", "Cardiology").queryParam("assignee", "nurse.kim").queryParam("page", 2).queryParam("size", 50)
                .when().get("/api/v1/tasks").then().statusCode(200);
        verify(taskService).search(Arrays.asList(Task.Status.values()), List.of("SCHEDULING"), "diabetes-management", "Cardiology", "nurse.kim", 2, 50);

        given().queryParam("status", "RESOLVED").when().get("/api/v1/tasks").then().statusCode(200);
        verify(taskService).search(List.of(Task.Status.RESOLVED), List.of(), null, null, null, 1, 100);

        given().queryParam("taskType", "SCHEDULING").queryParam("taskType", "REFERRAL").when().get("/api/v1/tasks").then().statusCode(200);
        verify(taskService).search(TaskRepository.ACTIVE, List.of("SCHEDULING", "REFERRAL"), null, null, null, 1, 100);
    }

    @Test
    void listRejectsAnUnknownStatus() {
        given().queryParam("status", "DONE").when().get("/api/v1/tasks")
                .then().statusCode(400).body("violations.field", hasItem("list.status"));
        verify(taskService, never()).search(anyList(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void getReturnsTheTaskWithItsHistoryOr404() {
        when(taskService.find(7L)).thenReturn(Optional.of(referral(Task.Status.OPEN,
                List.of(new TaskEventDto(NOW, null, Task.Status.OPEN, "SYSTEM", "created from evaluation run 3")))));
        when(taskService.find(8L)).thenReturn(Optional.empty());

        given().when().get("/api/v1/tasks/7")
                .then().statusCode(200)
                .body("events[0].toStatus", is("OPEN"))
                .body("events[0]", not(hasKey("fromStatus")));
        given().when().get("/api/v1/tasks/8").then().statusCode(404);
    }

    @Test
    void patchAppliesTheChange() {
        when(taskService.update(7L, new TaskUpdateRequest("nurse.kim", Task.Status.IN_PROGRESS, "nurse.kim", null)))
                .thenReturn(Optional.of(referral(Task.Status.IN_PROGRESS, List.of())));

        given().contentType("application/json").body("{\"actor\":\"nurse.kim\",\"status\":\"IN_PROGRESS\",\"assignee\":\"nurse.kim\"}")
                .when().patch("/api/v1/tasks/7")
                .then().statusCode(200).body("status", is("IN_PROGRESS"));
    }

    @Test
    void patchMapsInvalidChangesTo400And409AndMissingTasksTo404() {
        when(taskService.update(eq(7L), any())).thenThrow(new InvalidTaskChangeException("A OPEN task can change to [...]", false));
        when(taskService.update(eq(8L), any())).thenThrow(new InvalidTaskChangeException("Task 8 is COMPLETED", true));
        when(taskService.update(eq(9L), any())).thenReturn(Optional.empty());
        String body = "{\"actor\":\"nurse.kim\",\"status\":\"OPEN\"}";

        given().contentType("application/json").body(body).when().patch("/api/v1/tasks/7")
                .then().statusCode(400).body("message", is("A OPEN task can change to [...]"));
        given().contentType("application/json").body(body).when().patch("/api/v1/tasks/8")
                .then().statusCode(409).body("message", is("Task 8 is COMPLETED"));
        given().contentType("application/json").body(body).when().patch("/api/v1/tasks/9").then().statusCode(404);
    }

    @Test
    void patchRequiresAnActor() {
        given().contentType("application/json").body("{\"status\":\"CANCELLED\"}").when().patch("/api/v1/tasks/7")
                .then().statusCode(400).body("violations.field", hasItem("update.change.actor"));
        verify(taskService, never()).update(any(), any());
    }

    @Test
    void tasksCannotBeCreatedOrDeletedThroughTheApi() {
        given().contentType("application/json").body("{}").when().post("/api/v1/tasks").then().statusCode(405);
        given().when().delete("/api/v1/tasks/7").then().statusCode(405);
    }
}
