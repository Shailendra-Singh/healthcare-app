package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import me.shail.dto.LoadFileDto;
import me.shail.dto.LoadRunDto;
import me.shail.service.LoadRunService;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LoadRunResourceTest {

    static final String PATH = "/api/v1/etl-runs/latest";

    @InjectMock
    LoadRunService loadRunService;

    @Test
    void latestReturnsTheRunWithItsFiles() {
        String checksum = TestData.FAKER.hashing().sha256();
        OffsetDateTime finished = OffsetDateTime.now(ZoneOffset.UTC);
        LoadRunDto run = new LoadRunDto(9L, "SUCCEEDED", finished.minusSeconds(3), finished, null, 2,
                List.of(new LoadFileDto("labs.csv", checksum, 4096L, 120, 2),
                        new LoadFileDto("patients.csv", TestData.FAKER.hashing().sha256(), 1024L, 40, 0)));
        when(loadRunService.findLatest()).thenReturn(Uni.createFrom().item(run));

        given().when().get(PATH)
                .then().statusCode(200)
                .body("runId", is(9))
                .body("status", is("SUCCEEDED"))
                .body("rejectedRows", is(2))
                .body("files.fileName", contains("labs.csv", "patients.csv"))
                .body("files[0].checksum", is(checksum))
                .body("files[0].rowCount", is(120))
                .body("files[0].rejectedRows", is(2));
    }

    @Test
    void latestSucceededUsesTheStatusFilter() {
        when(loadRunService.findLatest("SUCCEEDED")).thenReturn(Uni.createFrom().item(
                new LoadRunDto(4L, "SUCCEEDED", null, null, null, 0, List.of())));

        given().queryParam("status", "SUCCEEDED").when().get(PATH).then().statusCode(200).body("runId", is(4));
        given().queryParam("status", "FAILED").when().get(PATH).then().statusCode(400);
    }

    @Test
    void latestReturns404BeforeTheFirstRun() {
        when(loadRunService.findLatest()).thenReturn(Uni.createFrom().nullItem());

        given().when().get(PATH).then().statusCode(404);
    }

    @Test
    void writesAreNotAllowed() {
        given().contentType("application/json").body("{}").when().post(PATH).then().statusCode(oneOf(404, 405));
        given().when().delete(PATH).then().statusCode(oneOf(404, 405));
    }
}
