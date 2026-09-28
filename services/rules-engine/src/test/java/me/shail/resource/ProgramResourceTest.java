package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Reads the repo's real care-programs/ folder. */
@QuarkusTest
class ProgramResourceTest {

    @Test
    void listsTheProgramsInTheFolder() {
        given().when().get("/api/v1/programs")
                .then().statusCode(200)
                .body("programs.id", containsInAnyOrder("diabetes-management", "primary-care-wellness"))
                .body("errors", empty());
    }

    @Test
    void getShowsTiersAndNeedsAndOmitsAMissingShortName() {
        given().when().get("/api/v1/programs/diabetes-management")
                .then().statusCode(200)
                .body("name", is("Diabetes Management"))
                .body("$", not(hasKey("shortName")))
                .body("sourceFile", is("diabetes-management.yaml"))
                .body("tiers.id", containsInAnyOrder("high-risk", "moderate-risk", "low-risk", "unmonitored"))
                .body("tiers.find { it.id == 'unmonitored' }.needs[0].note", is("Get labs done"))
                .body("tiers.find { it.id == 'unmonitored' }.needs[0].priority", is("high"));

        given().when().get("/api/v1/programs/primary-care-wellness").then().statusCode(200).body("shortName", is("PCP"));
    }

    @Test
    void unknownProgramIs404() {
        given().when().get("/api/v1/programs/no-such-program").then().statusCode(404);
    }
}
