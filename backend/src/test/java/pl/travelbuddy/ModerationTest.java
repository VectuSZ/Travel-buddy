package pl.travelbuddy;
import pl.travelbuddy.moderation.*;
import pl.travelbuddy.auth.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import java.util.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;
@QuarkusTest
class ModerationTest extends ApplicationServiceTest {
 @Inject ModerationService moderation;
 @Inject ReportChallenge challenge;
 @Inject io.quarkus.mailer.MockMailbox mailbox;
 @BeforeEach void administrator(){QuarkusTransaction.requiringNew().run(()->User.update("role='ADMIN' where id=?1",d.id));d.role="ADMIN";}
 @AfterEach void removeReports(){QuarkusTransaction.requiringNew().run(()->{ModerationAction.delete("targetTripId = ?1 or targetUserId in ?2",trip,List.of(a.id,b.id,c.id,d.id));Report.delete("targetTripId = ?1 or targetUserId in ?2",trip,List.of(a.id,b.id,c.id,d.id));});}
 ObjectNode report(){return json.createObjectNode().put("targetTripId",trip.toString()).put("targetUrl","/trips/"+trip).put("category","SPAM").put("description","Reported content").put("reporterName","Anonymous reporter").put("reporterEmail","reporter@example.test").put("goodFaith",true).put("token",challenge.issue());}
 ObjectNode reason(){return json.createObjectNode().put("reason","Reviewed content");}
 String token(User user){return QuarkusTransaction.requiringNew().call(()->sessions.create(User.findById(user.id)));}
 @Test void reportingValidationAndAudit(){
  UUID anonymous=moderation.report(null,report()),logged=moderation.report(b,report());
  QuarkusTransaction.requiringNew().run(()->{Report r=Report.findById(anonymous);assertNull(r.reporterId);assertEquals("reporter@example.test",r.reporterEmail);Report s=Report.findById(logged);assertEquals(b.id,s.reporterId);assertNull(s.reporterEmail);});
  assertThrows(AuthProblem.class,()->moderation.report(null,report().put("reporterId",a.id.toString())));
  assertThrows(AuthProblem.class,()->moderation.report(null,report().put("targetUserId",a.id.toString())));
  var missing=report();missing.remove("targetTripId");assertThrows(AuthProblem.class,()->moderation.report(null,missing));
  assertThrows(AuthProblem.class,()->moderation.report(null,report().put("targetTripId",UUID.randomUUID().toString())));
  assertThrows(AuthProblem.class,()->moderation.report(null,report().put("category","INVALID")));
  assertThrows(AuthProblem.class,()->moderation.report(null,report().put("description","x".repeat(4001))));
  assertThrows(AuthProblem.class,()->moderation.report(null,report().put("token","fake")));
  var reused=report();moderation.report(null,reused);assertThrows(AuthProblem.class,()->moderation.report(null,reused));
  assertThrows(AuthProblem.class,()->moderation.list(b,"OPEN"));
  moderation.resolve(d,anonymous,reason().put("status","RESOLVED"));
  assertFalse(mailbox.getMailsSentTo("reporter@example.test").isEmpty());
  QuarkusTransaction.requiringNew().run(()->{ModerationAction audit=ModerationAction.find("reportId",anonymous).firstResult();assertEquals(d.id,audit.adminId);assertEquals("Reviewed content",audit.reason);assertNotNull(audit.createdAt);});
  var csrf=given().get("/api/v1/auth/csrf");
  for(String endpoint:List.of("/admin/reports","/admin/access"))given().cookie("session",token(b)).get("/api/v1"+endpoint).then().statusCode(403);
  given().cookie("session",token(b)).cookies(csrf.cookies()).header("Origin","http://localhost:3000").header("X-CSRF-TOKEN",csrf.path("token")).contentType("application/json").body(reason().toString()).post("/api/v1/admin/trips/"+trip+"/hide").then().statusCode(403);
  String adminJson=given().cookie("session",token(d)).get("/api/v1/admin/reports").asString();
  assertFalse(adminJson.contains("reporter@example.test"));assertFalse(adminJson.contains("private-"));
 }
 @Test void hideRestoreBlockAndContacts(){
  var app=apply(b);service.decide(a,app.id(),"accept");
  String oldSession=token(a);
  moderation.act(d,trip,"hide",reason());
  given().get("/api/v1/trips/"+trip).then().statusCode(404);
  given().get("/api/v1/trips?country=PL").then().body("items.id",org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(trip.toString())));
  assertThrows(AuthProblem.class,()->service.contacts(b,trip,a.id));
  moderation.act(d,trip,"unhide",reason());
  given().get("/api/v1/trips/"+trip).then().statusCode(200);
  int v=trips.mine(a,trip).version();trips.transition(a,trip,json.createObjectNode().put("version",v),false);
  moderation.act(d,trip,"hide",reason());moderation.act(d,trip,"unhide",reason());
  given().get("/api/v1/trips/"+trip).then().statusCode(200).body("status",org.hamcrest.Matchers.is("CLOSED"));
  moderation.act(d,a.id,"block",reason());
  given().cookie("session",oldSession).get("/api/v1/auth/session").then().statusCode(401);
  given().get("/api/v1/trips/"+trip).then().statusCode(404);
  given().get("/api/v1/trips?country=PL").then().body("items.id",org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(trip.toString())));
  given().get("/api/v1/profiles/"+a.id).then().statusCode(404);
  assertThrows(AuthProblem.class,()->service.contacts(b,trip,a.id));
  moderation.act(d,a.id,"unblock",reason());
  given().cookie("session",oldSession).get("/api/v1/auth/session").then().statusCode(401);
  given().get("/api/v1/trips/"+trip).then().statusCode(200);
 }
 @Test void reportHttpLimit(){
  var csrf=given().get("/api/v1/auth/csrf");
  for(int i=0;i<6;i++){
   var request=given().cookies(csrf.cookies()).header("Origin","http://localhost:3000").header("X-CSRF-TOKEN",csrf.path("token")).contentType("application/json").body(report().toString());
   if(i==1)request.cookie("session",token(b));
   request.post("/api/v1/reports").then().statusCode(i<5?200:429);
  }
 }
}
