package pl.travelbuddy.auth;
import pl.travelbuddy.profiles.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
@QuarkusTest
class ProfileResourceTest {
 @Inject RateLimiter limiter;
 String email,session,id; Response csrf;
 @BeforeEach void setup() {
  limiter.clear(); email="profile-test-"+UUID.randomUUID()+"@example.test";
  csrf=given().get("/api/v1/auth/csrf");
  session=write().body(Map.of("email",email,"password","Long profile password 123!","passwordConfirmation","Long profile password 123!","locale","pl","acceptTerms",true,"termsVersion","local-draft-1")).post("/api/v1/auth/register").then().statusCode(201).extract().cookie("session");
  id=me().path("userId");
 }
 @AfterEach void cleanup() { QuarkusTransaction.requiringNew().run(()->User.delete("email",email)); }
 RequestSpecification write() { var r=given().contentType("application/json").cookies(csrf.cookies()).header("Origin","http://localhost:3000").header("X-CSRF-TOKEN",csrf.path("token")); if(session!=null)r.cookie("session",session);return r; }
 Response me() { return given().cookie("session",session).get("/api/v1/me"); }
 Map<String,Object> initial() { return new HashMap<>(Map.of("displayName","  Traveler  ","age",30,"languages",List.of("pl","en","pl"),"travelStyles",List.of("trekking","trekking"),"version",0)); }
 Response create() { return write().body(initial()).patch("/api/v1/me/profile"); }
 @Test void firstAndPartialSaveWithAgeDateAndVersion() {
  me().then().statusCode(200).header("Cache-Control",is("private, no-store")).body("profile",nullValue()).body("contacts.contactEmail",nullValue()).body("profileComplete",is(false)).body("hasContact",is(false));
  int version=create().then().statusCode(200).body("profile.displayName",is("Traveler")).body("profile.languages",contains("pl","en")).body("profile.travelStyles",contains("trekking")).body("profileComplete",is(true)).body("readyToParticipate",is(false)).extract().path("profile.version");
  assertEquals(1,version);
  QuarkusTransaction.requiringNew().run(()->TravelerProfile.update("ageUpdatedAt = ?1 where userId = ?2",LocalDate.of(2020,1,1),UUID.fromString(id)));
  version=write().body(Map.of("bio","New bio","version",version)).patch("/api/v1/me/profile").then().statusCode(200).body("profile.ageUpdatedAt",is("2020-01-01")).extract().path("profile.version");
  version=write().body(Map.of("age",30,"version",version)).patch("/api/v1/me/profile").then().statusCode(200).body("profile.ageUpdatedAt",is("2020-01-01")).extract().path("profile.version");
  write().body(Map.of("age",31,"version",version)).patch("/api/v1/me/profile").then().statusCode(200).body("profile.ageUpdatedAt",is(LocalDate.now(ZoneOffset.UTC).toString()));
  write().body(Map.of("bio","stale","version",version)).patch("/api/v1/me/profile").then().statusCode(409).body("code",is("PROFILE_VERSION_CONFLICT"));
 }
 @Test void validationBoundariesAndUnknownFields() {
  write().body(Map.of("version",0,"bio","incomplete")).patch("/api/v1/me/profile").then().statusCode(400);
  for(var entry:Map.<String,Object>of("displayName","x","age",17,"languages",List.of("xx"),"travelStyles",List.of("unknown"),"bio","<b>html</b>","userId",UUID.randomUUID().toString(),"role","ADMIN").entrySet()) {
   var input=initial();input.put(entry.getKey(),entry.getValue());write().body(input).patch("/api/v1/me/profile").then().statusCode(400);
  }
  for(var entry:Map.<String,Object>of("displayName","x".repeat(61),"age",121,"bio","x".repeat(1001),"languages",List.of("pl","en","de","fr","es","it","pt","uk","cs","sk","nl")).entrySet()) {
   var input=initial();input.put(entry.getKey(),entry.getValue());write().body(input).patch("/api/v1/me/profile").then().statusCode(400);
  }
  var input=initial();input.put("displayName","x".repeat(60));input.put("age",18);input.put("bio","x".repeat(1000));
  int v=write().body(input).patch("/api/v1/me/profile").then().statusCode(200).extract().path("profile.version");
  var patch=new HashMap<String,Object>();patch.put("version",v);patch.put("displayName",null);
  write().body(patch).patch("/api/v1/me/profile").then().statusCode(400);
  patch.remove("displayName");patch.put("bio",null);patch.put("age",120);
  write().body(patch).patch("/api/v1/me/profile").then().statusCode(200).body("profile.bio",nullValue());
 }
 @Test void contactsReplacementFlagsAndPublicWhitelist() {
  write().body(Map.of("contactEmail"," contact@example.test ","instagramHandle","@some_one","whatsappE164","+48123456789")).put("/api/v1/me/contacts").then().statusCode(200).body("hasContact",is(true)).body("readyToParticipate",is(false));
  create().then().statusCode(200).body("readyToParticipate",is(true));
  Response pub=given().get("/api/v1/profiles/"+id);
  pub.then().statusCode(200).header("Cache-Control",is("private, no-store")).body("keySet()",containsInAnyOrder("userId","displayName","age","bio","languages","travelStyles"));
  assertFalse(pub.asString().contains(email));assertFalse(pub.asString().contains("contact@example.test"));assertFalse(pub.asString().contains("some_one"));assertFalse(pub.asString().contains("48123456789"));
  write().body(Map.of("instagramHandle","replacement")).put("/api/v1/me/contacts").then().statusCode(200).body("contacts.contactEmail",nullValue()).body("contacts.whatsappE164",nullValue());
  write().body(Map.of("contactEmail"," ")).put("/api/v1/me/contacts").then().statusCode(200).body("hasContact",is(false)).body("readyToParticipate",is(false));
 }
 @Test void invalidContactsAndMassAssignment() {
  for(var entry:Map.<String,Object>of("contactEmail","invalid","instagramHandle","https://instagram.com/a","whatsappE164","4812345","email","new@example.test","userId",UUID.randomUUID().toString()).entrySet()) {
   write().body(Map.of(entry.getKey(),entry.getValue())).put("/api/v1/me/contacts").then().statusCode(400);
  }
  me().then().body("email",is(email)).body("hasContact",is(false));
 }
 @Test void authenticationCsrfAndInactivePublicProfiles() {
  given().get("/api/v1/me").then().statusCode(401);
  given().contentType("application/json").cookie("session",session).header("Origin","http://localhost:3000").body(initial()).patch("/api/v1/me/profile").then().statusCode(403);
  write().header("X-CSRF-TOKEN",Tokens.random()).body(Map.of()).put("/api/v1/me/contacts").then().statusCode(403);
  given().get("/api/v1/profiles/"+id).then().statusCode(404);create().then().statusCode(200);
  for(String status:List.of("BLOCKED","DELETION_PENDING")) {
   QuarkusTransaction.requiringNew().run(()->User.update("status = ?1 where email = ?2",status,email));
   given().get("/api/v1/profiles/"+id).then().statusCode(404);me().then().statusCode(401);
  }
 }
 @Test void concurrentInitialWritesHaveOneWinner() throws Exception {
  try(var executor=Executors.newFixedThreadPool(2)) {
   var latch=new CountDownLatch(1); Callable<Integer> call=()->{latch.await();return create().statusCode();};
   var a=executor.submit(call);var b=executor.submit(call);latch.countDown();
   assertEquals(List.of(200,409),java.util.stream.Stream.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)).sorted().toList());
  }
 }
}
