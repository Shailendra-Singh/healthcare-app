package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.time.LocalDate;
import java.util.List;
import me.shail.dto.CareNeedDto;
import me.shail.rules.ProgramEvaluator.NeedStatus;
import me.shail.service.ResultsService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CareNeedResourceTest {

    @InjectMock
    ResultsService resultsService;

    @Test
    void passesFiltersAndPaging() {
        when(resultsService.careNeeds("diabetes-management", "high-risk", NeedStatus.OVERDUE, "Cardiology", 2, 50))
                .thenReturn(List.of(new CareNeedDto("P1", "diabetes-management", "high-risk", "Cardiology", 90,
                        LocalDate.of(2026, 5, 1), LocalDate.of(2026, 7, 30), null, NeedStatus.OVERDUE, "normal", null, null)));

        given().queryParam("programId", "diabetes-management").queryParam("tierId", "high-risk")
                .queryParam("status", "OVERDUE").queryParam("specialty", "Cardiology")
                .queryParam("page", 2).queryParam("size", 50)
                .when().get("/api/v1/care-needs")
                .then().statusCode(200)
                .body("[0].dueDate", is("2026-07-30"))
                .body("[0].everyDays", is(90));
    }

    @Test
    void defaultsToTheFirst100WithNoFilters() {
        when(resultsService.careNeeds(null, null, null, null, 1, 100)).thenReturn(List.of());

        given().when().get("/api/v1/care-needs").then().statusCode(200);
        verify(resultsService).careNeeds(null, null, null, null, 1, 100);
    }

    @Test
    void rejectsSizeAbove1000() {
        given().queryParam("size", 1001).when().get("/api/v1/care-needs")
                .then().statusCode(400).body("violations.field", hasItem("list.size"));
        verify(resultsService, never()).careNeeds(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void rejectsAnUnknownStatus() {
        given().queryParam("status", "LATE").when().get("/api/v1/care-needs")
                .then().statusCode(400).body("violations[0].message", is("must be MET, SCHEDULED or OVERDUE"));
        verify(resultsService, never()).careNeeds(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void writesAreNotAllowed() {
        given().contentType("application/json").body("{}").when().post("/api/v1/care-needs").then().statusCode(405);
    }
}
