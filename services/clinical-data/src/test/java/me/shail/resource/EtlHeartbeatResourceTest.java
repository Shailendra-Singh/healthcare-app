package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import me.shail.dto.EtlHeartbeatDto;
import me.shail.service.EtlHeartbeatService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EtlHeartbeatResourceTest {

    static final String PATH = "/api/v1/etl-heartbeat";

    @InjectMock
    EtlHeartbeatService etlHeartbeatService;

    @Test
    void returnsTheLastCheck() {
        OffsetDateTime checkedAt = OffsetDateTime.of(2026, 9, 28, 10, 0, 0, 0, ZoneOffset.UTC);
        when(etlHeartbeatService.find()).thenReturn(Uni.createFrom().item(new EtlHeartbeatDto(
                checkedAt, "unchanged", "Files unchanged since run 3", 300, checkedAt.plusMinutes(5), false)));

        given().when().get(PATH)
                .then().statusCode(200)
                .body("outcome", is("unchanged"))
                .body("detail", is("Files unchanged since run 3"))
                .body("intervalSeconds", is(300))
                .body("checkedAt", is("2026-09-28T10:00:00Z"))
                .body("nextCheckDueBy", is("2026-09-28T10:05:00Z"))
                .body("stale", is(false));
    }

    @Test
    void returns404BeforeTheFirstCheck() {
        when(etlHeartbeatService.find()).thenReturn(Uni.createFrom().nullItem());

        given().when().get(PATH).then().statusCode(404);
    }

    @Test
    void writesAreNotAllowed() {
        given().contentType("application/json").body("{}").when().put(PATH).then().statusCode(oneOf(404, 405));
        given().when().delete(PATH).then().statusCode(oneOf(404, 405));
    }
}
