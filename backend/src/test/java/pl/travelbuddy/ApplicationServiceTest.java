package pl.travelbuddy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import pl.travelbuddy.auth.*;
import pl.travelbuddy.profiles.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@QuarkusTest
class ApplicationServiceTest {
 @Inject ApplicationService service;
 @Inject TripService trips;
 @Inject SessionService sessions;
 @Inject ObjectMapper json;
 User a,b,c,d;UUID trip;
 User user(){
  User u=new User();u.id=UUID.randomUUID();u.email=u.id+"@example.test";u.role="USER";u.status="ACTIVE";u.uiLocale="pl";u.termsVersion="test";u.termsAcceptedAt=u.createdAt=u.updatedAt=Instant.now();u.persist();
  TravelerProfile p=new TravelerProfile();p.userId=u.id;p.displayName="Traveler";p.age=30;p.ageUpdatedAt=LocalDate.now();p.languages=new String[]{"pl"};p.travelStyles=new String[]{"trekking"};p.createdAt=p.updatedAt=Instant.now();p.persist();
  ProfileContacts contact=new ProfileContacts();contact.userId=u.id;contact.contactEmail="private-"+u.id+"@example.test";contact.updatedAt=Instant.now();contact.persist();return u;
 }
 @BeforeEach void setup(){QuarkusTransaction.requiringNew().run(()->{
  a=user();b=user();c=user();d=user();
  Trip t=new Trip();t.id=UUID.randomUUID();trip=t.id;t.organizerId=a.id;t.title="Test";t.itinerary="Plan";t.lookingFor="Travelers";t.contentLanguage="pl";t.dateMode="EXACT";t.dateFrom=LocalDate.of(2027,1,1);t.dateTo=LocalDate.of(2027,1,4);t.budgetMin=java.math.BigDecimal.ZERO;t.budgetMax=java.math.BigDecimal.TEN;t.targetPeople=3;t.styles=new String[]{"trekking"};t.currency="PLN";t.status="OPEN";t.moderationStatus="VISIBLE";t.createdAt=t.updatedAt=t.publishedAt=Instant.now();t.persist();
  TripLocation l=new TripLocation();l.id=UUID.randomUUID();l.tripId=trip;l.countryCode="PL";l.position=0;l.persist();
 });}
 @AfterEach void cleanup(){QuarkusTransaction.requiringNew().run(()->{TripApplication.delete("tripId",trip);TripLocation.delete("tripId",trip);Trip.deleteById(trip);for(User u:List.of(a,b,c,d))User.deleteById(u.id);});}
 ApplicationService.View apply(User u){return service.apply(u,trip,json.createObjectNode().put("message","Private application message"));}
 @Test void relationshipWithdrawalAndTerms(){
  var ab=apply(b);var ac=apply(c);service.decide(a,ab.id(),"accept");service.decide(a,ac.id(),"accept");
  assertNotNull(service.contacts(b,trip,a.id).contactEmail());assertNotNull(service.contacts(a,trip,b.id).contactEmail());
  assertThrows(AuthProblem.class,()->service.contacts(b,trip,c.id));
  assertEquals(3,service.participants(trip).size());
  service.decide(b,ab.id(),"withdraw");assertThrows(AuthProblem.class,()->service.contacts(b,trip,a.id));assertEquals(2,service.participants(trip).size());
  int version=trips.mine(a,trip).version();
  assertThrows(AuthProblem.class,()->trips.patch(a,trip,json.createObjectNode().put("version",version).put("budgetMax",20)));
  trips.patch(a,trip,json.createObjectNode().put("version",version).put("itinerary","Updated description"));
  var ad=apply(d);
  int current=trips.mine(a,trip).version();
  trips.transition(a,trip,json.createObjectNode().put("version",current),false);
  assertEquals("REJECTED",service.mine(d).getFirst().status());
  assertNotNull(service.contacts(c,trip,a.id).contactEmail());
  assertThrows(AuthProblem.class,()->service.decide(a,ad.id(),"accept"));
  QuarkusTransaction.requiringNew().run(()->Trip.update("moderationStatus='HIDDEN' where id=?1",trip));
  assertThrows(AuthProblem.class,()->service.contacts(c,trip,a.id));
  QuarkusTransaction.requiringNew().run(()->{Trip.update("moderationStatus='VISIBLE' where id=?1",trip);User.update("status='BLOCKED' where id=?1",a.id);});
  assertThrows(AuthProblem.class,()->service.contacts(c,trip,a.id));
 }
 @Test void lastPlaceRace() throws Exception {
  QuarkusTransaction.requiringNew().run(()->Trip.update("targetPeople=2 where id=?1",trip));
  var ab=apply(b);var ac=apply(c);
  try(var pool=Executors.newFixedThreadPool(2)){
   var latch=new CountDownLatch(1);
   java.util.function.Function<UUID,Callable<Boolean>> call=id->()->{latch.await();try{service.decide(a,id,"accept");return true;}catch(AuthProblem e){assertEquals(409,e.status);assertEquals("TRIP_FULL",e.code);return false;}};
   var one=pool.submit(call.apply(ab.id()));var two=pool.submit(call.apply(ac.id()));latch.countDown();
   assertNotEquals(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));
  }
  assertEquals(2,service.participants(trip).size());
 }
 @Test void withdrawnFirstAcceptanceStillLocksTerms(){
  var ab=apply(b);service.decide(a,ab.id(),"accept");service.decide(b,ab.id(),"withdraw");
  int version=trips.mine(a,trip).version();
  assertThrows(AuthProblem.class,()->trips.patch(a,trip,json.createObjectNode().put("version",version).put("dateTo","2027-02-01")));
  assertThrows(AuthProblem.class,()->trips.patch(a,trip,json.createObjectNode().put("version",version).put("targetPeople",2)));
 }
 @Test void privateHttpContract(){
  var ab=apply(b);service.decide(a,ab.id(),"accept");
  String token=QuarkusTransaction.requiringNew().call(()->sessions.create(User.findById(b.id)));
  var response=io.restassured.RestAssured.given().cookie("session",token).get("/api/v1/trips/"+trip+"/contacts/"+a.id);
  assertEquals(200,response.statusCode());assertEquals("private, no-store",response.header("Cache-Control"));
  assertEquals(Set.of("contactEmail","instagramHandle","whatsappE164"),response.jsonPath().getMap("").keySet());
  io.restassured.RestAssured.given().cookie("session",token).contentType("application/json").body("{}").post("/api/v1/applications/"+ab.id()+"/withdraw").then().statusCode(403);
  String publicJson=io.restassured.RestAssured.given().get("/api/v1/trips/"+trip+"/participants").asString();
  assertFalse(publicJson.contains("private-"));assertFalse(publicJson.contains("Private application message"));
  service.decide(b,ab.id(),"withdraw");
  io.restassured.RestAssured.given().cookie("session",token).get("/api/v1/trips/"+trip+"/contacts/"+a.id).then().statusCode(403);
 }
 @Test void rejectionAndAuthorization(){
  var ab=apply(b);
  assertThrows(AuthProblem.class,()->service.decide(c,ab.id(),"accept"));
  assertThrows(AuthProblem.class,()->service.incoming(c,trip));
  assertThrows(AuthProblem.class,()->apply(b));
  assertThrows(AuthProblem.class,()->apply(a));
  service.decide(a,ab.id(),"reject");
  assertThrows(AuthProblem.class,()->service.contacts(b,trip,a.id));
  assertThrows(AuthProblem.class,()->service.decide(b,ab.id(),"withdraw"));
 }
}
