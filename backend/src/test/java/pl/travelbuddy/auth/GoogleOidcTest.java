package pl.travelbuddy.auth;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
@QuarkusTest @QuarkusTestResource(value=OidcProvider.class,restrictToAnnotatedClass=true)
class GoogleOidcTest {
 @Inject RateLimiter limiter;
 String email,sub;
 final UUID applicationId=UUID.randomUUID();
 final UUID tripId=UUID.randomUUID();
 @BeforeEach void before() { limiter.clear(); email="oidc-test-"+UUID.randomUUID()+"@example.test"; sub=UUID.randomUUID().toString(); }
 @AfterEach void after() { QuarkusTransaction.requiringNew().run(()->{
   pl.travelbuddy.TripApplication.delete("id",applicationId);
   pl.travelbuddy.Trip.delete("id",tripId); User.delete("email",email);
  }); }
 record Flow(Map<String,String> cookies,Map<String,String> query) {}
 String reauthenticationSession;
 Flow start(boolean reauthenticate) { return start(reauthenticate,false); }
 Flow start(boolean reauthenticate,boolean link) {
  Response csrf=given().get("/api/v1/auth/csrf");
  Response begin=given().cookies(csrf.cookies()).cookies(reauthenticationSession==null?Map.of():Map.of("session",reauthenticationSession)).header("Origin","http://localhost:3000").header("X-CSRF-TOKEN",csrf.path("token"))
   .contentType("application/json").body(Map.of("reauthenticate",reauthenticate,"link",link,"locale","en"))
   .post("/api/v1/auth/google/start");
  begin.then().statusCode(200);
  Map<String,String> cookies=new HashMap<>(begin.cookies());
  if(reauthenticationSession!=null) cookies.put("session",reauthenticationSession);
  Response authorize=given().redirects().follow(false).cookies(cookies).get("/api/v1/auth/google/authorize");
  authorize.then().statusCode(302); cookies.putAll(authorize.cookies());
  Map<String,String> query=OidcProvider.query(URI.create(authorize.header("Location")).getRawQuery());
  if(reauthenticate) assertEquals("0",query.get("max_age"));
  else assertEquals("select_account",query.get("prompt"));
  assertFalse(query.containsKey("login_hint"));
  if(reauthenticate) assertEquals("0",query.get("max_age"));
  else assertEquals("select_account",query.get("prompt"));
  assertFalse(query.containsKey("login_hint"));
  assertNotNull(query.get("nonce")); assertEquals("S256",query.get("code_challenge_method"));
  assertEquals(Set.of("openid","email","profile"),new HashSet<>(Arrays.asList(query.get("scope").split(" "))));
  return new Flow(cookies,query);
 }
 Response finish(Flow f,Map<String,Object> overrides) {
  Map<String,Object> claims=new HashMap<>(Map.of("iss",OidcProvider.issuer,"sub",sub,"aud","test-client","iat",Instant.now().getEpochSecond(),"exp",Instant.now().plusSeconds(300).getEpochSecond(),"nonce",f.query.get("nonce"),"email",email,"email_verified",true));
  claims.putAll(overrides);
  String code=OidcProvider.grant(claims,f.query.get("code_challenge"));
  return given().redirects().follow(false).cookies(f.cookies).queryParam("state",f.query.get("state")).queryParam("code",code).get("/api/v1/auth/google/callback");
 }
 String pending(Flow flow) {
  return given().cookies(flow.cookies).get("/api/v1/auth/google/consent").then().statusCode(200).extract().path("confirmation");
 }
 Response decide(Flow flow,String confirmation,boolean accept) {
  Response csrf=given().get("/api/v1/auth/csrf");
  Map<String,String> cookies=new HashMap<>(flow.cookies);
  cookies.putAll(csrf.cookies());
  return given().cookies(cookies).header("Origin","http://localhost:3000").header("X-CSRF-TOKEN",csrf.path("token"))
    .contentType("application/json").body(Map.of("confirmation",confirmation,"accepted",accept,"termsVersion","local-draft-2")).post("/api/v1/auth/google/consent");
 }
 Response register() {
  Flow flow=start(false);
  Response callback=finish(flow,Map.of());
  callback.then().statusCode(303).header("Location",endsWith("/en/google-terms"));
  assertNull(callback.cookie("session"));
  assertTrue(callback.cookie("q_session")==null || callback.cookie("q_session").isEmpty());
  QuarkusTransaction.requiringNew().run(()->assertEquals(0,User.count("email",email)));
  String confirmation=pending(flow);
  Response session=decide(flow,confirmation,true);
  session.then().statusCode(204);
  decide(flow,confirmation,true).then().statusCode(400);
  return session;
 }
 @Test void declineConsumesPendingIdentityWithoutAccountOrSession() {
  Flow flow=start(false);
  assertNull(finish(flow,Map.of()).cookie("session"));
  String confirmation=pending(flow);
  given().cookies(flow.cookies).header("Origin","http://localhost:3000").contentType("application/json")
    .body(Map.of("confirmation",confirmation,"accepted",true,"termsVersion","local-draft-2")).post("/api/v1/auth/google/consent").then().statusCode(403);
  Response declined=decide(flow,confirmation,false);
  declined.then().statusCode(204);
  assertNull(declined.cookie("session"));
  decide(flow,confirmation,true).then().statusCode(400);
  QuarkusTransaction.requiringNew().run(()->assertEquals(0,User.count("email",email)));
 }
 @Test void deletionReauthenticationBindsIdentityAndSession() {
  reauthenticationSession=register().cookie("session");
  finish(start(true),Map.of("sub","different", "auth_time",Instant.now().plusSeconds(1).getEpochSecond()))
    .then().header("Location",containsString("GOOGLE_IDENTITY_MISMATCH"));
  finish(start(true),Map.of()).then().header("Location",containsString("REAUTHENTICATION_REQUIRED"));
  finish(start(true),Map.of("auth_time",Instant.now().plusSeconds(1).getEpochSecond()))
    .then().header("Location",endsWith("/en/account"));
  QuarkusTransaction.requiringNew().run(()-> {
   Session session=Session.find("tokenHash",Tokens.hash(reauthenticationSession)).firstResult();
   assertNotNull(session.deletionConfirmedUntil);
  });
 }
 @Test void newAccountAndExistingSubjectDespiteChangedEmail() {
  Response first=register();
  String session=first.cookie("session"); assertNotNull(session);
  given().cookie("session",session).get("/api/v1/auth/session").then().statusCode(200).body("keySet()",contains("email")).body("email",is(email));
  QuarkusTransaction.requiringNew().run(()-> { User u=User.find("email",email).firstResult(); assertNull(u.passwordHash); assertNotNull(u.emailVerifiedAt); assertEquals("en",u.uiLocale); assertEquals("local-draft-2",u.termsVersion); assertNotNull(u.termsAcceptedAt); assertEquals(1,AuthIdentity.count("user",u)); });
  Response again=finish(start(false),Map.of("email","changed@example.test")); again.then().statusCode(303).header("Location",endsWith("/en/account"));
  assertNotEquals(session,again.cookie("session"));
  String all=again.asString()+again.getHeaders(); assertFalse(all.contains(sub)); assertFalse(all.contains("provider-access-secret")); assertFalse(all.contains("provider-refresh-secret"));
 }
 @Test void businessRejectionsAndNoLinking() {
  finish(start(false),Map.of("email_verified",false)).then().header("Location",containsString("GOOGLE_EMAIL_UNVERIFIED"));
  QuarkusTransaction.requiringNew().run(()-> { User u=new User();u.id=UUID.randomUUID();u.email=email;u.passwordHash="unchanged";u.role="USER";u.status="ACTIVE";u.uiLocale="pl";u.termsVersion="local-draft-2";u.termsAcceptedAt=u.createdAt=u.updatedAt=Instant.now();u.persist(); });
  finish(start(false),Map.of()).then().header("Location",endsWith("/en/google-link"));
  QuarkusTransaction.requiringNew().run(()-> { User u=User.find("email",email).firstResult();assertEquals("unchanged",u.passwordHash);assertEquals(0,AuthIdentity.count("user",u)); });
 }
 @Inject AuthService auth;
 @Inject GoogleAttempts attempts;
 static final String PASSWORD="Local linking password 123";
 String local(String address) {
  return auth.register(new AuthDtos.Register(address,PASSWORD,PASSWORD,"en",true,"local-draft-2"));
 }
 String linkCode(Flow f) {
  return given().cookies(f.cookies).get("/api/v1/auth/google/link").then().statusCode(200).extract().path("confirmation");
 }
 Response confirmLink(Flow f,String code,String password,boolean cancel) {
  var csrf=given().get("/api/v1/auth/csrf");
  var cookies=new HashMap<>(f.cookies); cookies.putAll(csrf.cookies());
  return given().cookies(cookies).header("Origin","http://localhost:3000").header("X-CSRF-TOKEN",csrf.path("token"))
   .contentType("application/json").body(Map.of("confirmation",code,"password",password,"cancel",cancel)).post("/api/v1/auth/google/link");
 }
 @Test void linkKeepsExistingAccountAndBothLoginMethods() {
  local(email);
  UUID id=QuarkusTransaction.requiringNew().call(()->{
   User u=User.find("email",email).firstResult();
   var profile=new pl.travelbuddy.profiles.TravelerProfile();
   profile.userId=u.id; profile.displayName="Existing traveler"; profile.age=30; profile.ageUpdatedAt=java.time.LocalDate.now();
   profile.languages=new String[]{"en"}; profile.travelStyles=new String[]{"comfort"}; profile.createdAt=profile.updatedAt=Instant.now(); profile.persist();
   var draft=new pl.travelbuddy.Trip();
   draft.id=tripId; draft.organizerId=u.id; draft.title="Existing draft"; draft.status="DRAFT";
   draft.moderationStatus="VISIBLE"; draft.currency="PLN"; draft.createdAt=draft.updatedAt=Instant.now(); draft.persist();
   var contact=new pl.travelbuddy.profiles.ProfileContacts();
   contact.userId=u.id; contact.instagramHandle="test_handle"; contact.updatedAt=Instant.now(); contact.persist();
   var application=new pl.travelbuddy.TripApplication();
   application.id=applicationId; application.applicantId=u.id; application.tripId=UUID.fromString("11111111-1111-4111-8111-111111111111");
   application.message="Existing application"; application.status="WITHDRAWN"; application.createdAt=application.updatedAt=Instant.now(); application.persist();
   return u.id;
  });
  var f=start(false);
  assertNull(finish(f,Map.of()).cookie("session"));
  String code=linkCode(f); assertNotNull(code);
  assertEquals("",pending(f));
  given().cookies(f.cookies).header("Origin","http://localhost:3000").contentType("application/json")
    .body(Map.of("confirmation",code,"password",PASSWORD)).post("/api/v1/auth/google/link").then().statusCode(403);
  confirmLink(f,code,"wrong",false).then().statusCode(401);
  QuarkusTransaction.requiringNew().run(()->assertEquals(0,AuthIdentity.count("user.id",id)));
  var linked=confirmLink(f,code,PASSWORD,false);
  linked.then().statusCode(204);
  given().cookie("session",linked.cookie("session")).get("/api/v1/auth/google/methods").then().body("password",is(true),"google",is(true));
  confirmLink(f,code,PASSWORD,false).then().statusCode(400);
  assertNotNull(auth.login(new AuthDtos.Login(email,PASSWORD)));
  finish(start(false),Map.of()).then().header("Location",endsWith("/en/account")).cookie("session",notNullValue());
  QuarkusTransaction.requiringNew().run(()->{
   AuthIdentity identity=AuthIdentity.find("issuer = ?1 and subject = ?2",OidcProvider.issuer,sub).firstResult();
   assertEquals(id,identity.user.id); assertEquals(1,AuthIdentity.count("user.id",id));
   pl.travelbuddy.profiles.TravelerProfile profile=pl.travelbuddy.profiles.TravelerProfile.findById(id);
   assertEquals("Existing traveler",profile.displayName);
   pl.travelbuddy.Trip draft=pl.travelbuddy.Trip.findById(tripId);
   assertEquals(id,draft.organizerId); assertEquals("Existing draft",draft.title);
   pl.travelbuddy.profiles.ProfileContacts contact=pl.travelbuddy.profiles.ProfileContacts.findById(id);
   assertEquals("test_handle",contact.instagramHandle);
   pl.travelbuddy.TripApplication application=pl.travelbuddy.TripApplication.findById(applicationId);
   assertEquals(id,application.applicantId); assertEquals("WITHDRAWN",application.status); assertEquals("Existing application",application.message);
  });
 }
 @Test void cancelledExpiredAndBlockedLinkCannotBeConfirmed() {
  local(email);
  var f=start(false); finish(f,Map.of()); String code=linkCode(f);
  confirmLink(f,code,"",true).then().statusCode(204);
  confirmLink(f,code,PASSWORD,false).then().statusCode(400);
  f=start(false); finish(f,Map.of()); code=linkCode(f);
  attempts.clean(Instant.now().plusSeconds(601));
  confirmLink(f,code,PASSWORD,false).then().statusCode(400);
  f=start(false); finish(f,Map.of()); code=linkCode(f);
  QuarkusTransaction.requiringNew().run(()->User.update("status = 'BLOCKED' where email = ?1",email));
  confirmLink(f,code,PASSWORD,false).then().statusCode(403);
  QuarkusTransaction.requiringNew().run(()->assertEquals(0,AuthIdentity.count("subject",sub)));
 }
 @Test void settingsLinksOnlyToInitiatingAccountAndRejectsAnOwnedIdentity() {
  String other="other-"+email;
  try {
   reauthenticationSession=local(other);
   var f=start(false,true); finish(f,Map.of()).then().header("Location",endsWith("/en/google-link"));
   String code=linkCode(f);
   var missing=new HashMap<>(f.cookies); missing.remove("session");
   confirmLink(new Flow(missing,f.query),code,PASSWORD,false).then().statusCode(403);
   var linked=confirmLink(f,code,PASSWORD,false); linked.then().statusCode(204);
   given().cookie("session",linked.cookie("session")).get("/api/v1/auth/session").then().body("email",is(other));
   reauthenticationSession=local(email);
   finish(start(false,true),Map.of()).then().header("Location",containsString("GOOGLE_LINK_UNAVAILABLE"));
   QuarkusTransaction.requiringNew().run(()->{
    AuthIdentity identity=AuthIdentity.find("subject",sub).firstResult(); assertEquals(other,identity.user.email);
   });
  } finally { QuarkusTransaction.requiringNew().run(()->User.delete("email",other)); }
 }
 @Test void inactiveAccountCannotSignIn() {
  register();
  for(String status:List.of("BLOCKED","DELETION_PENDING")) {
   QuarkusTransaction.requiringNew().run(()->User.update("status = ?1 where email = ?2",status,email));
   finish(start(false),Map.of()).then().header("Location",containsString("GOOGLE_ACCOUNT_BLOCKED"));
  }
 }
 @Test void oidcRejectsIssuerAudienceNonceAndExpiry() {
  for(var override:List.<Map<String,Object>>of(Map.of("iss","https://evil.example"),Map.of("aud","other-client"),Map.of("nonce","wrong"),Map.of("exp",Instant.now().minusSeconds(60).getEpochSecond()))) {
   Response response=finish(start(false),override); assertNull(response.cookie("session")); assertTrue(response.statusCode()>=400 || response.header("Location").contains("GOOGLE_FAILED"));
  }
  QuarkusTransaction.requiringNew().run(()->assertEquals(0,User.count("email",email)));
 }
 @Test void stateMismatchAndMixedAttemptsFail() {
  Flow a=start(false),b=start(false);
  Map<String,String> mixed=new HashMap<>(a.cookies); mixed.put("google-flow",b.cookies.get("google-flow"));
  finish(new Flow(mixed,a.query),Map.of()).then().header("Location",containsString("GOOGLE_FLOW_INVALID"));
  Map<String,String> q=new HashMap<>(b.query);q.put("state","wrong-state");
  assertNull(finish(new Flow(b.cookies,q),Map.of()).cookie("session"));
 }
 @Test void concurrentConsentsForSameIdentityCreateOneAccount() throws Exception {
  Flow a=start(false),b=start(false);
  try(var executor=Executors.newFixedThreadPool(2)) {
   var latch=new CountDownLatch(1);
   finish(a,Map.of()); finish(b,Map.of());
   String first=pending(a),second=pending(b);
   var one=executor.submit(()->{latch.await();return decide(a,first,true);});
   var two=executor.submit(()->{latch.await();return decide(b,second,true);});latch.countDown();
   one.get(20,TimeUnit.SECONDS).then().statusCode(204);
   two.get(20,TimeUnit.SECONDS).then().statusCode(204);
  }
  QuarkusTransaction.requiringNew().run(()-> {assertEquals(1,User.count("email",email));assertEquals(1,AuthIdentity.count("issuer = ?1 and subject = ?2",OidcProvider.issuer,sub));});
 }
 @Test void startNeedsCsrfAndRejectsReturnUrl() {
  given().contentType("application/json").header("Origin","http://localhost:3000").body("{}").post("/api/v1/auth/google/start").then().statusCode(403);
  Response csrf=given().get("/api/v1/auth/csrf");
  for(var input:List.of(Map.of("locale","xx"),Map.of("locale","pl","returnUrl","https://evil.example"))) {
   given().cookies(csrf.cookies()).header("Origin","http://localhost:3000").header("X-CSRF-TOKEN",csrf.path("token"))
    .contentType("application/json").body(input).post("/api/v1/auth/google/start").then().statusCode(400).header("Cache-Control",is("no-store"));
  }
 }
 @Test void cancellationAndMissingVerificationCreateNoAccount() {
  Flow f=start(false);
  Response cancel=given().redirects().follow(false).cookies(f.cookies).queryParam("state",f.query.get("state")).queryParam("error","access_denied").get("/api/v1/auth/google/callback");
  assertNull(cancel.cookie("session"));
  given().redirects().follow(false).get("/api/v1/auth/google/error?error=access_denied").then().statusCode(303).header("Location",containsString("GOOGLE_CANCELLED"));
  var missing=new HashMap<String,Object>(); missing.put("email_verified",null);
  finish(start(false),missing).then().header("Location",containsString("GOOGLE_EMAIL_UNVERIFIED"));
  QuarkusTransaction.requiringNew().run(()->assertEquals(0,User.count("email",email)));
 }

 @Test void differentSubjectsRacingForSameEmailNeverLink() throws Exception {
  Flow a=start(false),b=start(false);
  try(var executor=Executors.newFixedThreadPool(2)) {
   var latch=new CountDownLatch(1);
   finish(a,Map.of()); finish(b,Map.of("sub",UUID.randomUUID().toString()));
   String first=pending(a),second=pending(b);
   var one=executor.submit(()->{latch.await();return decide(a,first,true);});
   var two=executor.submit(()->{latch.await();return decide(b,second,true);});latch.countDown();
   var responses=List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
   assertEquals(1,responses.stream().filter(r->r.statusCode()==204).count());
   responses.stream().filter(r->r.statusCode()!=204).findFirst().orElseThrow().then().statusCode(409).body("code",is("GOOGLE_EMAIL_CONFLICT"));
  }
  QuarkusTransaction.requiringNew().run(()-> { User u=User.find("email",email).firstResult();assertEquals(1,AuthIdentity.count("user",u)); });
 }

}
