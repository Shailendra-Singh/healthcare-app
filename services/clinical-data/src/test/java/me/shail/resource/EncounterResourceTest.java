package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import java.time.LocalDate;
import java.util.List;
import me.shail.dto.EncounterDto;
import me.shail.model.Patient;
import me.shail.service.EncounterService;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EncounterResourceTest {

    @InjectMock
    EncounterService encounterService;

    private final Patient patient = TestData.patient(1L, null, null);
    private final EncounterDto past = EncounterDto.from(TestData.encounter(
            5L, patient, TestData.specialty((short) 1), TestData.provider(3L), TestData.pastDate()));
    private final EncounterDto scheduled = EncounterDto.from(TestData.encounter(
            6L, patient, TestData.specialty((short) 2), null, LocalDate.now().plusMonths(1)));

    @Test
    void listReturnsPastAndScheduledEncounters() {
        when(encounterService.findByPatient(1L)).thenReturn(Uni.createFrom().item(List.of(scheduled, past)));

        given().when().get("/api/v1/patients/1/encounters").then().statusCode(200).body("id", contains(6, 5));
    }

    @Test
    void upcomingReturnsScheduledEncounters() {
        when(encounterService.findUpcoming(1L)).thenReturn(Uni.createFrom().item(List.of(scheduled)));

        given().when().get("/api/v1/patients/1/encounters/upcoming")
                .then().statusCode(200)
                .body("id", contains(6))
                .body("encounterDate", contains(scheduled.encounterDate().toString()));
    }

    @Test
    void getReturnsTheEncounterOfThatPatientOnly() {
        when(encounterService.findById(5L)).thenReturn(Uni.createFrom().item(past));

        given().when().get("/api/v1/patients/1/encounters/5")
                .then().statusCode(200)
                .body("specialty.name", is(past.specialty().name()))
                .body("provider.name", is(past.provider().name()));
        given().when().get("/api/v1/patients/2/encounters/5").then().statusCode(404);
    }

    @Test
    void encounterWithoutProviderSerializesANullProvider() {
        when(encounterService.findById(6L)).thenReturn(Uni.createFrom().item(scheduled));

        given().when().get("/api/v1/patients/1/encounters/6").then().statusCode(200).body("provider", nullValue());
    }

    @Test
    void getReturns404WhenMissing() {
        when(encounterService.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        given().when().get("/api/v1/patients/1/encounters/99").then().statusCode(404);
    }

    /**
     * Refused, but with 404 rather than 405: this path shares the /patients prefix with PatientResource,
     * and Quarkus REST then reports an unmatched method as "not found".
     */
    @Test
    void writesAreNotAllowed() {
        given().contentType("application/json").body("{}").when().post("/api/v1/patients/1/encounters").then().statusCode(oneOf(404, 405));
        verifyNoInteractions(encounterService);
    }
}
