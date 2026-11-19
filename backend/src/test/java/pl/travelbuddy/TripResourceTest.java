package pl.travelbuddy;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
@QuarkusTest
class TripResourceTest {
 @Test void listAndDetails() {
 given().when().get("/api/v1/trips").then().statusCode(200).body("items.size()",is(2)).body("items[0].title",is("Weekend w Tatrach"));
 given().when().get("/api/v1/trips/11111111-1111-4111-8111-111111111111").then().statusCode(200).body("country",is("Polska")).body("remainingPlaces",is(3)).body("keySet()",containsInAnyOrder("id","slug","title","country","description","startDate","endDate","contentLanguage","itinerary","lookingFor","dateMode","dateFrom","dateTo","durationDays","targetPeople","budgetMin","budgetMax","currency","styles","status","version","locations","organizer","remainingPlaces"));
 }
 @Test void missing() { given().get("/api/v1/trips/99999999-9999-4999-8999-999999999999").then().statusCode(404); }
}
