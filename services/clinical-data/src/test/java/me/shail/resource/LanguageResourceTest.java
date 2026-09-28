package me.shail.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import java.util.List;
import me.shail.dto.LanguageDto;
import me.shail.service.LanguageService;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LanguageResourceTest {

    static final String PATH = "/api/v1/languages";

    @InjectMock
    LanguageService languageService;

    private final LanguageDto first = LanguageDto.from(TestData.language((short) 1));

    @Test
    void listReturnsAll() {
        LanguageDto second = LanguageDto.from(TestData.language((short) 2));
        when(languageService.findAll()).thenReturn(Uni.createFrom().item(List.of(first, second)));

        given().when().get(PATH)
                .then().statusCode(200)
                .body("name", contains(first.name(), second.name()));
        verify(languageService, never()).findByName(anyString());
    }

    @Test
    void listWithNameReturnsTheMatch() {
        when(languageService.findByName("abc")).thenReturn(Uni.createFrom().item(first));

        given().queryParam("name", "abc").when().get(PATH)
                .then().statusCode(200)
                .body("name", contains(first.name()));
    }

    @Test
    void listWithUnknownNameReturnsAnEmptyList() {
        when(languageService.findByName("nope")).thenReturn(Uni.createFrom().nullItem());

        given().queryParam("name", "nope").when().get(PATH)
                .then().statusCode(200)
                .body("$", empty());
    }

    @Test
    void getReturnsOne() {
        when(languageService.findById((short) 1)).thenReturn(Uni.createFrom().item(first));

        given().when().get(PATH + "/1")
                .then().statusCode(200)
                .body("id", is(1))
                .body("name", is(first.name()));
    }

    @Test
    void getReturns404WhenMissing() {
        when(languageService.findById(Short.valueOf((short) 99))).thenReturn(Uni.createFrom().nullItem());

        given().when().get(PATH + "/99").then().statusCode(404);
    }

    @Test
    void writesAreNotAllowed() {
        given().contentType("application/json").body("{\"name\":\"x\"}").when().post(PATH).then().statusCode(405);
        given().contentType("application/json").body("{\"name\":\"x\"}").when().put(PATH + "/1").then().statusCode(405);
        given().when().delete(PATH + "/1").then().statusCode(405);
    }
}
