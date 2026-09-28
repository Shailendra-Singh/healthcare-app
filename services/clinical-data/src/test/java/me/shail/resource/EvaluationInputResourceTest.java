package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import me.shail.dto.EvaluationInputDto;
import me.shail.service.EvaluationInputService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EvaluationInputResourceTest {

    static final String PATH = "/api/v1/evaluation-inputs";

    @InjectMock
    EvaluationInputService evaluationInputService;

    @Test
    void returnsPatientsWithTheirFacts() {
        EvaluationInputDto input = new EvaluationInputDto(1L, "P1", LocalDate.of(1960, 1, 1), 'F',
                List.of(new EvaluationInputDto.Diagnosis("E11.65", "E11", true, LocalDate.of(2020, 1, 1))),
                List.of(new EvaluationInputDto.LabResult("HbA1c", new BigDecimal("9.4"), LocalDate.of(2026, 5, 2))),
                List.of(new EvaluationInputDto.Visits("Endocrinology", LocalDate.of(2026, 1, 5), null)));
        when(evaluationInputService.findPage(2, 100, LocalDate.of(2026, 6, 1))).thenReturn(Uni.createFrom().item(List.of(input)));

        given().queryParam("page", 2).queryParam("size", 100).queryParam("asOf", "2026-06-01").when().get(PATH)
                .then().statusCode(200)
                .body("sourcePatientId", contains("P1"))
                .body("[0].diagnoses[0].conditionGroup", is("E11"))
                .body("[0].diagnoses[0].chronic", is(true))
                .body("[0].latestLabResults[0].resultValue", is(9.4f))
                .body("[0].visits[0].lastVisitDate", is("2026-01-05"));
    }

    @Test
    void defaultsToPageOneOf500AsOfToday() {
        when(evaluationInputService.findPage(1, 500, LocalDate.now())).thenReturn(Uni.createFrom().item(List.of()));

        given().when().get(PATH).then().statusCode(200);
        verify(evaluationInputService).findPage(1, 500, LocalDate.now());
    }

    @Test
    void rejectsSizeAbove1000() {
        given().queryParam("size", 1001).when().get(PATH).then().statusCode(400).body("violations.field", hasItem("list.size"));
        verify(evaluationInputService, never()).findPage(anyLong(), anyInt(), any());
    }
}
