package pl.travelbuddy;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
@QuarkusTest
class TripResourceTest {
 @Test void listAndDetails() {
 given().when().get("/api/v1/trips").then().statusCode(200).body("size()",is(2)).body("[0].title",is("Weekend w Tatrach"));
 given().when().get("/api/v1/trips/11111111-1111-4111-8111-111111111111").then().statusCode(200).body("country",is("Polska")).body("keySet()",containsInAnyOrder("id","title","country","description","startDate","endDate","contentLanguage"));
 }
 @Test void missing() { given().get("/api/v1/trips/99999999-9999-4999-8999-999999999999").then().statusCode(404); }
}
