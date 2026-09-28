package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import java.util.List;
import me.shail.dto.ConditionGroupDto;
import me.shail.service.ConditionGroupService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConditionGroupResourceTest {

    static final String PATH = "/api/v1/condition-groups";

    @InjectMock
    ConditionGroupService conditionGroupService;

    private final ConditionGroupDto diabetes = new ConditionGroupDto((short) 2, "E11", "Type 2 Diabetes", true);

    @Test
    void listReturnsAll() {
        when(conditionGroupService.findAll()).thenReturn(Uni.createFrom().item(List.of(diabetes)));

        given().when().get(PATH).then().statusCode(200).body("icdPrefix", contains("E11"));
    }

    @Test
    void listWithIcdPrefixReturnsTheMatch() {
        when(conditionGroupService.findByIcdPrefix("E11")).thenReturn(Uni.createFrom().item(diabetes));

        given().queryParam("icdPrefix", "E11").when().get(PATH)
                .then().statusCode(200)
                .body("conditionName", contains("Type 2 Diabetes"))
                .body("chronic", contains(true));
    }

    @Test
    void listWithUnknownIcdPrefixReturnsAnEmptyList() {
        when(conditionGroupService.findByIcdPrefix("X99")).thenReturn(Uni.createFrom().nullItem());

        given().queryParam("icdPrefix", "X99").when().get(PATH).then().statusCode(200).body("$", empty());
    }

    @Test
    void getReturnsOneOr404() {
        when(conditionGroupService.findById((short) 2)).thenReturn(Uni.createFrom().item(diabetes));
        when(conditionGroupService.findById((short) 99)).thenReturn(Uni.createFrom().nullItem());

        given().when().get(PATH + "/2").then().statusCode(200).body("icdPrefix", is("E11"));
        given().when().get(PATH + "/99").then().statusCode(404);
    }

    @Test
    void writesAreNotAllowed() {
        given().contentType("application/json").body("{}").when().post(PATH).then().statusCode(405);
        given().when().delete(PATH + "/2").then().statusCode(405);
    }
}
