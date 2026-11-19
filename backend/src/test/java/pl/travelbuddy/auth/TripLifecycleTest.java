package pl.travelbuddy.auth;
import pl.travelbuddy.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import java.util.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
@QuarkusTest
class TripLifecycleTest {
 @Inject RateLimiter limiter;
 Response csrf;String session,owner;List<String> emails=new ArrayList<>();
 RequestSpecification write(){return given().contentType("application/json").cookies(csrf.cookies()).cookie("session",session).header("Origin","http://localhost:3000").header("X-CSRF-TOKEN",csrf.path("token"));}
 String register(){
  String email="trip-"+UUID.randomUUID()+"@example.test";emails.add(email);
  return given().contentType("application/json").cookies(csrf.cookies()).header("Origin","http://localhost:3000").header("X-CSRF-TOKEN",csrf.path("token"))
   .body(Map.of("email",email,"password","Long trip password 123!","passwordConfirmation","Long trip password 123!","locale","pl","acceptTerms",true,"termsVersion","local-draft-2"))
   .post("/api/v1/auth/register").then().statusCode(201).extract().cookie("session");
 }
 @BeforeEach void setup(){limiter.clear();csrf=given().get("/api/v1/auth/csrf");session=register();owner=given().cookie("session",session).get("/api/v1/me").path("userId");}
 @AfterEach void cleanup(){QuarkusTransaction.requiringNew().run(()->{
  TripLocation.delete("tripId in (select t.id from Trip t where t.organizerId = ?1)",UUID.fromString(owner));
  Trip.delete("organizerId",UUID.fromString(owner));for(String email:emails)User.delete("email",email);
 });}
 Map<String,Object> full(int v){var n=new HashMap<String,Object>();n.putAll(Map.of("version",v,"title","Trip test","itinerary","A quiet trip","lookingFor","Travel companions","contentLanguage","en","dateMode","FLEXIBLE","dateFrom","2027-06-01","dateTo","2027-06-10","durationDays",10,"targetPeople",2));n.putAll(Map.of("budgetMin",0,"budgetMax",100,"styles",List.of("trekking"),"locations",List.of(Map.of("countryCode","PL","city","Zakopane"),Map.of("countryCode","SK"))));return n;}
 @Test void lifecycleAndAccess() {
  String id=write().body(Map.of("title","Draft")).post("/api/v1/trips").then().statusCode(201).body("status",is("DRAFT")).extract().path("id");
  String url="/api/v1/trips/"+id;
  int v=given().cookie("session",session).get("/api/v1/me/trips/"+id).then().statusCode(200).body("title",is("Draft")).extract().path("version");
  given().cookie("session",session).get("/api/v1/me/trips").then().body("id",hasItem(id));
  given().get(url).then().statusCode(404);
  String a=session;session=register();
  given().cookie("session",session).get("/api/v1/me/trips/"+id).then().statusCode(404);
  write().body(full(v)).patch(url).then().statusCode(404);session=a;
  given().contentType("application/json").cookie("session",session).body(full(v)).patch(url).then().statusCode(403);
  int newer=write().body(full(v)).patch(url).then().statusCode(200).body("locations.size()",is(2)).extract().path("version");
  write().body(Map.of("version",v,"title","Stale")).patch(url).then().statusCode(409);
  given().cookie("session",session).get("/api/v1/me/trips/"+id).then().body("title",is("Trip test"));
  write().body(Map.of("version",newer)).post(url+"/publish").then().statusCode(400).body("code",is("INVALID_PROFILE_REQUIRED"));
  write().body(Map.of("version",0,"displayName","Organizer","age",30,"languages",List.of("pl"),"travelStyles",List.of("trekking"))).patch("/api/v1/me/profile").then().statusCode(200);
  write().body(Map.of("version",newer)).post(url+"/publish").then().statusCode(400).body("code",is("INVALID_CONTACT_REQUIRED"));
  write().body(Map.of("contactEmail","private@example.test")).put("/api/v1/me/contacts").then().statusCode(200);
  int published=write().body(Map.of("version",newer)).post(url+"/publish").then().statusCode(200).body("status",is("OPEN")).extract().path("version");
  given().get("/api/v1/trips").then().body("items.id",hasItem(id));
  given().get(url).then().statusCode(200).body("organizer.displayName",is("Organizer")).body(not(containsString("private@example.test")));
  write().body(Map.of("version",published,"title","")).patch(url).then().statusCode(400);
  write().body(Map.of("version",published)).post(url+"/close").then().statusCode(200).body("status",is("CLOSED"));
  given().get("/api/v1/trips").then().body("items.id",not(hasItem(id)));
  given().get(url).then().statusCode(200).body("status",is("CLOSED"));
  QuarkusTransaction.requiringNew().run(()->Trip.update("moderationStatus = 'HIDDEN' where id = ?1",UUID.fromString(id)));
  given().get(url).then().statusCode(404);
  QuarkusTransaction.requiringNew().run(()->{Trip.update("moderationStatus = 'VISIBLE' where id = ?1",UUID.fromString(id));User.update("status = 'BLOCKED' where id = ?1",UUID.fromString(owner));});
  given().get(url).then().statusCode(404);
 }
 @Test void atomicValidationAndMassAssignment(){
  String id=write().body(Map.of()).post("/api/v1/trips").then().statusCode(201).extract().path("id");
  int v=given().cookie("session",session).get("/api/v1/me/trips/"+id).path("version");
  var n=full(v);n.put("locations",List.of(Map.of("countryCode","PL"),Map.of("countryCode","XX")));
  write().body(n).patch("/api/v1/trips/"+id).then().statusCode(400);
  given().cookie("session",session).get("/api/v1/me/trips/"+id).then().body("title",nullValue()).body("locations.size()",is(0)).body("version",is(v));
  n=full(v);n.put("durationDays",11);write().body(n).patch("/api/v1/trips/"+id).then().statusCode(400);
  for(String key:List.of("organizerId","status","moderationStatus","publishedAt"))write().body(Map.of(key,"OPEN")).post("/api/v1/trips").then().statusCode(400);
 }
}
