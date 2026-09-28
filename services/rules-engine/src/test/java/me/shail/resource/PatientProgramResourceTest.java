package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import me.shail.dto.CareNeedDto;
import me.shail.dto.PatientProgramDto;
import me.shail.rules.ProgramEvaluator.NeedStatus;
import me.shail.service.ResultsService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PatientProgramResourceTest {

    @InjectMock
    ResultsService resultsService;

    @Test
    void returnsProgramsTiersEvidenceAndNeeds() {
        CareNeedDto need = new CareNeedDto("P1", "diabetes-management", "unmonitored", "Endocrinology", 90, null,
                LocalDate.of(2026, 9, 28), null, NeedStatus.OVERDUE, "high", "Get labs done");
        when(resultsService.patientPrograms("P1")).thenReturn(List.of(new PatientProgramDto("diabetes-management",
                "Diabetes Management", null, "unmonitored", "Unmonitored",
                Map.of("HbA1c", "no result in the last 6 months"), List.of(need))));

        given().when().get("/api/v1/patients/P1/programs")
                .then().statusCode(200)
                .body("[0].programId", is("diabetes-management"))
                .body("[0]", not(hasKey("shortName")))
                .body("[0].tierName", is("Unmonitored"))
                .body("[0].evidence.HbA1c", is("no result in the last 6 months"))
                .body("[0].needs[0].status", is("OVERDUE"))
                .body("[0].needs[0].lastVisitDate", is((Object) null))
                .body("[0].needs[0].note", is("Get labs done"));
    }

    @Test
    void aPatientInNoProgramGetsAnEmptyList() {
        when(resultsService.patientPrograms("P9")).thenReturn(List.of());

        given().when().get("/api/v1/patients/P9/programs").then().statusCode(200).body("$", empty());
    }
}
