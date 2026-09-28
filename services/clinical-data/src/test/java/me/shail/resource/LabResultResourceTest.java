package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import java.util.List;
import me.shail.dto.LabResultDto;
import me.shail.service.LabResultService;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LabResultResourceTest {

    @InjectMock
    LabResultService labResultService;

    private final LabResultDto result = LabResultDto.from(TestData.labResult(
            4L, TestData.patient(1L, null, null), TestData.labTest((short) 1), TestData.pastDate()));

    @Test
    void listReturnsThePatientsResults() {
        when(labResultService.findByPatient(1L)).thenReturn(Uni.createFrom().item(List.of(result)));

        given().when().get("/api/v1/patients/1/lab-results")
                .then().statusCode(200)
                .body("id", contains(4))
                .body("labTest.name", contains(result.labTest().name()));
    }

    @Test
    void latestReturnsTheMostRecentResultOfTheTest() {
        when(labResultService.findLatest(1L, "HbA1c")).thenReturn(Uni.createFrom().item(result));

        given().queryParam("test", "HbA1c").when().get("/api/v1/patients/1/lab-results/latest")
                .then().statusCode(200)
                .body("id", is(4))
                .body("resultValue", is(result.resultValue().floatValue()))
                .body("resultDate", is(result.resultDate().toString()));
    }

    @Test
    void latestReturns404WhenThePatientHasNoSuchResult() {
        when(labResultService.findLatest(1L, "LDL")).thenReturn(Uni.createFrom().nullItem());

        given().queryParam("test", "LDL").when().get("/api/v1/patients/1/lab-results/latest").then().statusCode(404);
    }

    @Test
    void latestRequiresTheTestParameter() {
        given().when().get("/api/v1/patients/1/lab-results/latest")
                .then().statusCode(400)
                .body("violations.field", hasItem("latest.test"));
        given().queryParam("test", " ").when().get("/api/v1/patients/1/lab-results/latest").then().statusCode(400);
        verify(labResultService, never()).findLatest(anyLong(), anyString());
    }

    @Test
    void getReturnsTheResultOfThatPatientOnly() {
        when(labResultService.findById(4L)).thenReturn(Uni.createFrom().item(result));

        given().when().get("/api/v1/patients/1/lab-results/4").then().statusCode(200).body("patientId", is(1));
        given().when().get("/api/v1/patients/2/lab-results/4").then().statusCode(404);
    }

    /**
     * Refused, but with 404 rather than 405: this path shares the /patients prefix with PatientResource,
     * and Quarkus REST then reports an unmatched method as "not found".
     */
    @Test
    void writesAreNotAllowed() {
        given().contentType("application/json").body("{}").when().post("/api/v1/patients/1/lab-results").then().statusCode(oneOf(404, 405));
        verifyNoInteractions(labResultService);
    }
}
