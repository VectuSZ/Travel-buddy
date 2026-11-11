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
 @BeforeEach void before() { limiter.clear(); email="oidc-test-"+UUID.randomUUID()+"@example.test"; sub=UUID.randomUUID().toString(); }
 @AfterEach void after() { QuarkusTransaction.requiringNew().run(()->User.delete("email",email)); }
 record Flow(Map<String,String> cookies,Map<String,String> query) {}
 Flow start(String mode) {
  Response csrf=given().get("/api/v1/auth/csrf");
  Response begin=given().cookies(csrf.cookies()).header("Origin","http://localhost:3000").header("X-CSRF-TOKEN",csrf.path("token"))
   .contentType("application/json").body(Map.of("mode",mode,"locale","en","acceptTerms",true,"termsVersion","local-draft-1"))
   .post("/api/v1/auth/google/start");
  begin.then().statusCode(200);
  Map<String,String> cookies=new HashMap<>(begin.cookies());
  Response authorize=given().redirects().follow(false).cookies(cookies).get("/api/v1/auth/google/authorize");
  authorize.then().statusCode(302); cookies.putAll(authorize.cookies());
  Map<String,String> query=OidcProvider.query(URI.create(authorize.header("Location")).getRawQuery());
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
 @Test void newAccountAndExistingSubjectDespiteChangedEmail() {
  Response first=finish(start("register"),Map.of()); first.then().statusCode(303).header("Location",is("http://localhost:3000/en/account"));
  String session=first.cookie("session"); assertNotNull(session);
  assertTrue(first.cookie("q_session")==null || first.cookie("q_session").isEmpty());
  given().cookie("session",session).get("/api/v1/auth/session").then().statusCode(200).body("keySet()",contains("email")).body("email",is(email));
  QuarkusTransaction.requiringNew().run(()-> { User u=User.find("email",email).firstResult(); assertNull(u.passwordHash); assertNotNull(u.emailVerifiedAt); assertEquals("en",u.uiLocale); assertEquals("local-draft-1",u.termsVersion); assertEquals(1,AuthIdentity.count("user",u)); });
  Response again=finish(start("login"),Map.of("email","changed@example.test")); again.then().statusCode(303).header("Location",endsWith("/en/account"));
  assertNotEquals(session,again.cookie("session"));
  String all=again.asString()+again.getHeaders(); assertFalse(all.contains(sub)); assertFalse(all.contains("provider-access-secret")); assertFalse(all.contains("provider-refresh-secret"));
 }
 @Test void businessRejectionsAndNoLinking() {
  finish(start("login"),Map.of()).then().header("Location",containsString("GOOGLE_REGISTRATION_REQUIRED"));
  finish(start("register"),Map.of("email_verified",false)).then().header("Location",containsString("GOOGLE_EMAIL_UNVERIFIED"));
  QuarkusTransaction.requiringNew().run(()-> { User u=new User();u.id=UUID.randomUUID();u.email=email;u.passwordHash="unchanged";u.role="USER";u.status="ACTIVE";u.uiLocale="pl";u.termsVersion="local-draft-1";u.termsAcceptedAt=u.createdAt=u.updatedAt=Instant.now();u.persist(); });
  finish(start("register"),Map.of()).then().header("Location",containsString("GOOGLE_EMAIL_CONFLICT"));
  QuarkusTransaction.requiringNew().run(()-> { User u=User.find("email",email).firstResult();assertEquals("unchanged",u.passwordHash);assertEquals(0,AuthIdentity.count("user",u)); });
 }
 @Test void inactiveAccountCannotSignIn() {
  finish(start("register"),Map.of()).then().statusCode(303);
  for(String status:List.of("BLOCKED","DELETION_PENDING")) {
   QuarkusTransaction.requiringNew().run(()->User.update("status = ?1 where email = ?2",status,email));
   finish(start("login"),Map.of()).then().header("Location",containsString("GOOGLE_ACCOUNT_BLOCKED"));
  }
 }
 @Test void oidcRejectsIssuerAudienceNonceAndExpiry() {
  for(var override:List.<Map<String,Object>>of(Map.of("iss","https://evil.example"),Map.of("aud","other-client"),Map.of("nonce","wrong"),Map.of("exp",Instant.now().minusSeconds(60).getEpochSecond()))) {
   Response response=finish(start("register"),override); assertNull(response.cookie("session")); assertTrue(response.statusCode()>=400 || response.header("Location").contains("GOOGLE_FAILED"));
  }
  QuarkusTransaction.requiringNew().run(()->assertEquals(0,User.count("email",email)));
 }
 @Test void stateMismatchAndMixedAttemptsFail() {
  Flow a=start("register"),b=start("login");
  Map<String,String> mixed=new HashMap<>(a.cookies); mixed.put("google-flow",b.cookies.get("google-flow"));
  finish(new Flow(mixed,a.query),Map.of()).then().header("Location",containsString("GOOGLE_FLOW_INVALID"));
  Map<String,String> q=new HashMap<>(b.query);q.put("state","wrong-state");
  assertNull(finish(new Flow(b.cookies,q),Map.of()).cookie("session"));
 }
 @Test void sameIdentityConcurrentCallbacksCreateOneAccount() throws Exception {
  Flow a=start("register"),b=start("register");
  try(var executor=Executors.newFixedThreadPool(2)) {
   var latch=new CountDownLatch(1);
   var one=executor.submit(()->{latch.await();return finish(a,Map.of());});
   var two=executor.submit(()->{latch.await();return finish(b,Map.of());});latch.countDown();
   one.get(20,TimeUnit.SECONDS).then().statusCode(303).header("Location",endsWith("/en/account"));
   two.get(20,TimeUnit.SECONDS).then().statusCode(303).header("Location",endsWith("/en/account"));
  }
  QuarkusTransaction.requiringNew().run(()-> {assertEquals(1,User.count("email",email));assertEquals(1,AuthIdentity.count("issuer = ?1 and subject = ?2",OidcProvider.issuer,sub));});
 }
 @Test void startNeedsCsrfTermsAndRejectsReturnUrl() {
  given().contentType("application/json").header("Origin","http://localhost:3000").body("{}").post("/api/v1/auth/google/start").then().statusCode(403);
  Response csrf=given().get("/api/v1/auth/csrf");
  for(var input:List.of(Map.of("mode","register","locale","pl","acceptTerms",false),Map.of("mode","login","locale","pl","returnUrl","https://evil.example"))) {
   given().cookies(csrf.cookies()).header("Origin","http://localhost:3000").header("X-CSRF-TOKEN",csrf.path("token"))
    .contentType("application/json").body(input).post("/api/v1/auth/google/start").then().statusCode(400).header("Cache-Control",is("no-store"));
  }
 }
 @Test void cancellationAndMissingVerificationCreateNoAccount() {
  Flow f=start("register");
  Response cancel=given().redirects().follow(false).cookies(f.cookies).queryParam("state",f.query.get("state")).queryParam("error","access_denied").get("/api/v1/auth/google/callback");
  assertNull(cancel.cookie("session"));
  given().redirects().follow(false).get("/api/v1/auth/google/error?error=access_denied").then().statusCode(303).header("Location",containsString("GOOGLE_CANCELLED"));
  var missing=new HashMap<String,Object>(); missing.put("email_verified",null);
  finish(start("register"),missing).then().header("Location",containsString("GOOGLE_EMAIL_UNVERIFIED"));
  QuarkusTransaction.requiringNew().run(()->assertEquals(0,User.count("email",email)));
 }

 @Test void differentSubjectsRacingForSameEmailNeverLink() throws Exception {
  Flow a=start("register"),b=start("register");
  try(var executor=Executors.newFixedThreadPool(2)) {
   var latch=new CountDownLatch(1);
   var one=executor.submit(()->{latch.await();return finish(a,Map.of());});
   var two=executor.submit(()->{latch.await();return finish(b,Map.of("sub",UUID.randomUUID().toString()));});latch.countDown();
   var locations=List.of(one.get(20,TimeUnit.SECONDS).header("Location"),two.get(20,TimeUnit.SECONDS).header("Location"));
   assertEquals(1,locations.stream().filter(l->l.endsWith("/en/account")).count());
   assertEquals(1,locations.stream().filter(l->l.contains("GOOGLE_EMAIL_CONFLICT")).count());
  }
  QuarkusTransaction.requiringNew().run(()-> { User u=User.find("email",email).firstResult();assertEquals(1,AuthIdentity.count("user",u)); });
 }

}
