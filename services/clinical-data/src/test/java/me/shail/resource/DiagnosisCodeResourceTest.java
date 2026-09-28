package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import java.util.List;
import me.shail.dto.ConditionGroupDto;
import me.shail.dto.DiagnosisCodeDto;
import me.shail.service.DiagnosisCodeService;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DiagnosisCodeResourceTest {

    static final String PATH = "/api/v1/diagnosis-codes";

    @InjectMock
    DiagnosisCodeService diagnosisCodeService;

    private final DiagnosisCodeDto diabetes = new DiagnosisCodeDto("E11.65", TestData.FAKER.disease().anyDisease(),
            new ConditionGroupDto((short) 2, "E11", "Type 2 Diabetes", true));
    private final DiagnosisCodeDto checkup = new DiagnosisCodeDto("Z00.00", TestData.FAKER.disease().anyDisease(), null);

    @Test
    void listReturnsAllCodes() {
        when(diagnosisCodeService.findAll()).thenReturn(Uni.createFrom().item(List.of(diabetes, checkup)));

        given().when().get(PATH).then().statusCode(200).body("icdCode", contains("E11.65", "Z00.00"));
        verify(diagnosisCodeService, never()).findChronic();
    }

    @Test
    void listWithChronicReturnsOnlyChronicCodes() {
        when(diagnosisCodeService.findChronic()).thenReturn(Uni.createFrom().item(List.of(diabetes)));

        given().queryParam("chronic", true).when().get(PATH)
                .then().statusCode(200)
                .body("icdCode", contains("E11.65"))
                .body("conditionGroup.icdPrefix", contains("E11"));
        verify(diagnosisCodeService, never()).findAll();
    }

    @Test
    void getReturnsTheCodeWithItsGroup() {
        when(diagnosisCodeService.findById("E11.65")).thenReturn(Uni.createFrom().item(diabetes));

        given().when().get(PATH + "/E11.65")
                .then().statusCode(200)
                .body("description", is(diabetes.description()))
                .body("conditionGroup.conditionName", is("Type 2 Diabetes"));
    }

    @Test
    void codeWithoutGroupSerializesANullGroup() {
        when(diagnosisCodeService.findById("Z00.00")).thenReturn(Uni.createFrom().item(checkup));

        given().when().get(PATH + "/Z00.00").then().statusCode(200).body("conditionGroup", nullValue());
    }

    @Test
    void getReturns404WhenMissing() {
        when(diagnosisCodeService.findById("Z99.9")).thenReturn(Uni.createFrom().nullItem());

        given().when().get(PATH + "/Z99.9").then().statusCode(404);
    }

    @Test
    void writesAreNotAllowed() {
        given().contentType("application/json").body("{}").when().post(PATH).then().statusCode(405);
        given().when().delete(PATH + "/E11.65").then().statusCode(405);
    }
}
