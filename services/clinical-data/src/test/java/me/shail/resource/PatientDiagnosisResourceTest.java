package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import java.util.List;
import me.shail.dto.PatientDiagnosisDto;
import me.shail.service.PatientDiagnosisService;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PatientDiagnosisResourceTest {

    @InjectMock
    PatientDiagnosisService patientDiagnosisService;

    private final PatientDiagnosisDto diagnosis = PatientDiagnosisDto.from(TestData.patientDiagnosis(
            7L, TestData.patient(1L, null, null), TestData.diagnosisCode(TestData.conditionGroup((short) 2))));

    @Test
    void listReturnsThePatientsDiagnoses() {
        when(patientDiagnosisService.findByPatient(1L)).thenReturn(Uni.createFrom().item(List.of(diagnosis)));

        given().when().get("/api/v1/patients/1/diagnoses")
                .then().statusCode(200)
                .body("id", contains(7))
                .body("diagnosisCode.icdCode", contains(diagnosis.diagnosisCode().icdCode()));
    }

    @Test
    void getReturnsTheDiagnosisOfThatPatient() {
        when(patientDiagnosisService.findById(7L)).thenReturn(Uni.createFrom().item(diagnosis));

        given().when().get("/api/v1/patients/1/diagnoses/7")
                .then().statusCode(200)
                .body("patientId", is(1))
                .body("diagnosedDate", is(diagnosis.diagnosedDate().toString()));
    }

    @Test
    void getReturns404ForAnotherPatientsDiagnosis() {
        when(patientDiagnosisService.findById(7L)).thenReturn(Uni.createFrom().item(diagnosis));

        given().when().get("/api/v1/patients/2/diagnoses/7").then().statusCode(404);
    }

    @Test
    void getReturns404WhenMissing() {
        when(patientDiagnosisService.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        given().when().get("/api/v1/patients/1/diagnoses/99").then().statusCode(404);
    }

    /**
     * Refused, but with 404 rather than 405: this path shares the /patients prefix with PatientResource,
     * and Quarkus REST then reports an unmatched method as "not found".
     */
    @Test
    void writesAreNotAllowed() {
        given().contentType("application/json").body("{}").when().post("/api/v1/patients/1/diagnoses").then().statusCode(oneOf(404, 405));
        verifyNoInteractions(patientDiagnosisService);
    }
}
