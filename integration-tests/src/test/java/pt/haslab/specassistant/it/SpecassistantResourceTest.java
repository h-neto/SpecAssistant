package pt.haslab.specassistant.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class SpecassistantResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/specassistant")
                .then()
                .statusCode(200)
                .body(is("Hello specassistant"));
    }
}
