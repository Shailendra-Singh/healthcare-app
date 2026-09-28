package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import java.util.List;
import me.shail.dto.PatientDto;
import me.shail.service.PatientService;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PatientResourceTest {

    static final String PATH = "/api/v1/patients";

    @InjectMock
    PatientService patientService;

    private final PatientDto patient =
            PatientDto.from(TestData.patient(1L, TestData.language((short) 2), TestData.provider(3L)));

    @Test
    void listUsesPageOneAndSizeTwentyByDefault() {
        when(patientService.findPage(1, 20)).thenReturn(Uni.createFrom().item(List.of(patient)));

        given().when().get(PATH).then().statusCode(200).body("sourcePatientId", contains(patient.sourcePatientId()));
        verify(patientService).findPage(1, 20);
    }

    @Test
    void listPassesPageAndSize() {
        when(patientService.findPage(3, 50)).thenReturn(Uni.createFrom().item(List.of()));

        given().queryParam("page", 3).queryParam("size", 50).when().get(PATH).then().statusCode(200);
        verify(patientService).findPage(3, 50);
    }

    @Test
    void listRejectsPageBelowOne() {
        given().queryParam("page", 0).when().get(PATH)
                .then().statusCode(400)
                .body("violations.field", hasItem("list.page"));
        verify(patientService, never()).findPage(anyLong(), anyInt());
    }

    @Test
    void listRejectsSizeOutsideOneToHundred() {
        given().queryParam("size", 101).when().get(PATH).then().statusCode(400).body("violations.field", hasItem("list.size"));
        given().queryParam("size", 0).when().get(PATH).then().statusCode(400).body("violations.field", hasItem("list.size"));
    }

    @Test
    void getReturnsThePatientWithNestedLookups() {
        when(patientService.findById(1L)).thenReturn(Uni.createFrom().item(patient));

        given().when().get(PATH + "/1")
                .then().statusCode(200)
                .body("id", is(1))
                .body("firstName", is(patient.firstName()))
                .body("dateOfBirth", is(patient.dateOfBirth().toString()))
                .body("gender", is(String.valueOf(patient.gender())))
                .body("language.name", is(patient.language().name()))
                .body("pcpProvider.name", is(patient.pcpProvider().name()));
    }

    @Test
    void getReturns404WhenMissing() {
        when(patientService.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        given().when().get(PATH + "/99").then().statusCode(404);
    }

    @Test
    void getBySourceIdReturnsThePatientOr404() {
        when(patientService.findBySourcePatientId(patient.sourcePatientId())).thenReturn(Uni.createFrom().item(patient));
        when(patientService.findBySourcePatientId("P0")).thenReturn(Uni.createFrom().nullItem());

        given().when().get(PATH + "/source/" + patient.sourcePatientId()).then().statusCode(200).body("id", is(1));
        given().when().get(PATH + "/source/P0").then().statusCode(404);
    }

    @Test
    void writesAreNotAllowed() {
        given().contentType("application/json").body("{}").when().post(PATH).then().statusCode(405);
        given().contentType("application/json").body("{}").when().put(PATH + "/1").then().statusCode(405);
        given().contentType("application/json").body("{}").when().patch(PATH + "/1").then().statusCode(405);
        given().when().delete(PATH + "/1").then().statusCode(405);
    }
}
