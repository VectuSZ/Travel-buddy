package pl.travelbuddy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
@QuarkusTest
class TripSearchTest {
 List<UUID> ids=new ArrayList<>();
 @BeforeEach void seed(){QuarkusTransaction.requiringNew().run(()->{
  for(int i=0;i<3;i++){
   Trip t=new Trip();t.id=UUID.randomUUID();ids.add(t.id);t.organizerId=UUID.fromString("33333333-3333-4333-8333-333333333333");t.title="Porto Lisbon";t.itinerary="Route";t.status="OPEN";t.moderationStatus="VISIBLE";t.currency="PLN";t.publishedAt=Instant.parse("2030-01-01T00:00:00Z");t.createdAt=t.updatedAt=t.publishedAt;t.dateFrom=LocalDate.parse("2027-05-01");t.dateTo=LocalDate.parse("2027-05-10");t.budgetMin=new java.math.BigDecimal("1500");t.budgetMax=new java.math.BigDecimal("2200");t.styles=new String[]{"comfort"};t.persist();
   for(int j=0;j<2;j++){TripLocation l=new TripLocation();l.id=UUID.randomUUID();l.tripId=t.id;l.position=(short)j;l.countryCode="PT";l.city=j==0?"Porto":"Lisbon";l.persist();}
  }
 });}
 @AfterEach void cleanup(){QuarkusTransaction.requiringNew().run(()->{for(UUID id:ids){TripLocation.delete("tripId",id);Trip.deleteById(id);}});}
 @Test void intersectionsAndPaging(){
  String query="/api/v1/trips?country=PT&style=comfort&from=2027-05-10&to=2027-05-20&budgetMin=2200&budgetMax=2500";
  given().get(query).then().statusCode(200).body("total",is(3)).body("items.size()",is(3));
  given().get(query.replace("budgetMin=2200","budgetMin=2200.01")).then().body("total",is(0));
  given().get("/api/v1/trips?country=PT&style=comfort&budgetMax=1500&to=2027-05-01").then().body("total",is(3));
  given().get(query.replace("from=2027-05-10","from=2027-05-11")).then().body("total",is(0));
  var ordered=ids.stream().map(UUID::toString).sorted(Comparator.reverseOrder()).toList();
  for(int i=0;i<3;i++)given().get(query+"&size=1&page="+(i+1)).then().body("total",is(3)).body("items.size()",is(1)).body("items[0].id",is(ordered.get(i)));
  given().get(query+"&size=1&page=4").then().body("items.size()",is(0));
  for(String q:List.of("page=0","size=51","from=bad","from=2027-06-01&to=2027-01-01","budgetMin=9&budgetMax=2","country=XX","style=bad"))
   given().get("/api/v1/trips?"+q).then().statusCode(400).body("code",is("INVALID_SEARCH"));
 }
}
