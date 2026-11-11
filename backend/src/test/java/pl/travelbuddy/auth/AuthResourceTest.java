package pl.travelbuddy.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.mailer.MockMailbox;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AuthResourceTest {
    static final String PASSWORD = "A long test passphrase 123!";
    static final String NEW_PASSWORD = "A different long passphrase 456!";
    @Inject RateLimiter limiter;
    @Inject MockMailbox mailbox;
    @Inject AuthCleanup cleanup;
    String email;
    Response csrf;

    @BeforeEach void prepare() {
        limiter.clear();
        mailbox.clear();
        email = "auth-test-" + UUID.randomUUID() + "@example.test";
        csrf = given().get("/api/v1/auth/csrf");
        csrf.then().statusCode(200).header("Cache-Control", "no-store");
    }
    @AfterEach void removeOnlyThisTestUser() {
        QuarkusTransaction.requiringNew().run(() -> User.delete("email", email));
    }
    RequestSpecification request(String session) {
        var r = given().contentType("application/json").header("Origin", "http://localhost:3000")
                .header("X-CSRF-TOKEN", csrf.jsonPath().getString("token")).cookies(csrf.cookies());
        if (session != null) r.cookie("session", session);
        return r;
    }
    Map<String,Object> registration() {
        return new HashMap<>(Map.of("email", email, "password", PASSWORD, "passwordConfirmation", PASSWORD,
                "locale", "pl", "acceptTerms", true, "termsVersion", "local-draft-1"));
    }
    String register() {
        return request(null).body(registration()).post("/api/v1/auth/register").then().statusCode(201)
                .header("Cache-Control", "no-store").extract().cookie("session");
    }
    Response login(String address, String password) {
        return request(null).body(Map.of("email",address,"password",password)).post("/api/v1/auth/login");
    }
    void session(String token, int status) {
        var response = given().cookie("session",token).get("/api/v1/auth/session");
        response.then().statusCode(status).header("Cache-Control","no-store");
        if (status == 200) response.then().body("email",is(email)).body("keySet()",contains("email"));
    }
    void mutateUser(java.util.function.Consumer<User> action) {
        QuarkusTransaction.requiringNew().run(() -> action.accept(User.find("email", email).firstResult()));
    }
    String resetToken() {
        request(null).body(Map.of("email",email)).post("/api/v1/auth/password-reset/request").then().statusCode(202);
        String text = mailbox.getMailsSentTo(email).getLast().getText();
        var matcher = Pattern.compile("#token=([A-Za-z0-9_-]{43})").matcher(text);
        assertTrue(matcher.find());
        assertTrue(text.contains("http://localhost:3000/reset-password#"));
        return matcher.group(1);
    }
    Response confirm(String token) {
        return request(null).body(Map.of("token",token,"password",NEW_PASSWORD,"passwordConfirmation",NEW_PASSWORD))
                .post("/api/v1/auth/password-reset/confirm");
    }
    @Test void registrationLoginAndCookieContract() {
        String token=register();
        assertTrue(Tokens.valid(token)); session(token,200);
        Response response=login(email.toUpperCase(Locale.ROOT),PASSWORD);
        response.then().statusCode(204);
        String second=response.cookie("session"); assertNotEquals(token,second); session(second,200);
        String cookie=response.getHeaders().getValues("Set-Cookie").stream().filter(v -> v.startsWith("session=")).findFirst().orElseThrow();
        assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/")); assertFalse(cookie.contains("Secure"));
        mutateUser(u -> {
            assertEquals("USER",u.role); assertEquals("ACTIVE",u.status); assertNull(u.emailVerifiedAt);
            assertEquals("local-draft-1",u.termsVersion); assertNotNull(u.termsAcceptedAt);
            assertTrue(u.passwordHash.startsWith("$argon2id$")); assertFalse(u.passwordHash.contains(PASSWORD));
            Session s=Session.find("tokenHash",Tokens.hash(second)).firstResult();
            assertEquals(32,s.tokenHash.length);
            assertEquals(Duration.ofDays(7),Duration.between(s.createdAt,s.expiresAt));
        });
    }
    @Test void duplicateEmailAndNoPrivilegeAssignment() {
        var data=registration(); data.put("role","ADMIN"); data.put("status","BLOCKED");
        request(null).body(data).post("/api/v1/auth/register").then().statusCode(201);
        mutateUser(u -> { assertEquals("USER",u.role); assertEquals("ACTIVE",u.status); });
        data.put("email",email.toUpperCase(Locale.ROOT));
        request(null).body(data).post("/api/v1/auth/register").then().statusCode(409).body("code",is("EMAIL_UNAVAILABLE"));
    }
    @Test void validationAndNeutralLogin() {
        var data=registration(); data.put("password","short");
        request(null).body(data).post("/api/v1/auth/register").then().statusCode(400)
                .contentType("application/problem+json").body("code",is("VALIDATION_FAILED"));
        data=registration(); data.put("acceptTerms",false);
        request(null).body(data).post("/api/v1/auth/register").then().statusCode(400);
        data=registration(); data.put("email","not-an-email");
        request(null).body(data).post("/api/v1/auth/register").then().statusCode(400);
        data=registration(); data.put("password","x".repeat(1025));
        request(null).body(data).post("/api/v1/auth/register").then().statusCode(400);
        register();
        login(email,NEW_PASSWORD).then().statusCode(401).body("code",is("INVALID_CREDENTIALS"));
        login("unknown@example.test",NEW_PASSWORD).then().statusCode(401).body("code",is("INVALID_CREDENTIALS"));
    }
    @Test void longPassphraseAndConfirmation() {
        var data=registration(); data.put("password","long phrase ".repeat(80)); data.put("passwordConfirmation",data.get("password"));
        request(null).body(data).post("/api/v1/auth/register").then().statusCode(201);
        login(email,(String)data.get("password")).then().statusCode(204);
    }
    @Test void logoutExpiryAndInactiveAccounts() {
        String token=register();
        Response out=request(token).body(Map.of()).post("/api/v1/auth/logout");
        out.then().statusCode(204);
        assertTrue(out.getHeaders().getValues("Set-Cookie").stream().anyMatch(v -> v.startsWith("session=") && v.contains("Max-Age=0")));
        session(token,401);
        String expired=login(email,PASSWORD).cookie("session");
        QuarkusTransaction.requiringNew().run(() -> Session.update("expiresAt = ?1 where tokenHash = ?2",Instant.now().minusSeconds(1),Tokens.hash(expired)));
        session(expired,401);
        String active=login(email,PASSWORD).cookie("session");
        for (String status: List.of("BLOCKED","DELETION_PENDING")) {
            mutateUser(u -> u.status=status); session(active,401);
            login(email,PASSWORD).then().statusCode(401).body("code",is("INVALID_CREDENTIALS"));
        }
    }
    @Test void csrfAndOriginOnAllWrites() {
        for (String path:List.of("register","login","logout","password-reset/request","password-reset/confirm")) {
            given().contentType("application/json").header("Origin","http://localhost:3000").body("{}")
                    .post("/api/v1/auth/"+path).then().statusCode(403).contentType("application/problem+json").body("code",is("CSRF_INVALID"));
            given().contentType("application/json").header("Origin","http://localhost:3000").cookies(csrf.cookies())
                    .header("X-CSRF-TOKEN",Tokens.random()).body("{}").post("/api/v1/auth/"+path)
                    .then().statusCode(403).body("code",is("CSRF_INVALID"));
            request(null).header("Origin","https://attacker.example").body("{}").post("/api/v1/auth/"+path)
                    .then().statusCode(403).body("code",is("ORIGIN_REJECTED"));
        }
    }
    @Test void loginLimitsCannotBeBypassedWithForwardedHeaders() {
        for (int i=0;i<10;i++) request(null).header("X-Forwarded-For","192.0.2."+i)
                .body(Map.of("email",email,"password",PASSWORD)).post("/api/v1/auth/login").then().statusCode(401);
        login(email,PASSWORD).then().statusCode(429).body("code",is("RATE_LIMITED"));
    }
    @Test void registrationAndResetLimits() {
        register();
        for(int i=0;i<4;i++) request(null).body(registration()).post("/api/v1/auth/register").then().statusCode(409);
        request(null).body(registration()).post("/api/v1/auth/register").then().statusCode(429);
        for(int i=0;i<3;i++) request(null).body(Map.of("email",email)).post("/api/v1/auth/password-reset/request").then().statusCode(202);
        request(null).body(Map.of("email",email)).post("/api/v1/auth/password-reset/request").then().statusCode(429);
        limiter.clear();
        for(int i=0;i<10;i++) request(null).body(Map.of("email","missing"+i+"@example.test")).post("/api/v1/auth/password-reset/request").then().statusCode(202);
        request(null).body(Map.of("email","missing11@example.test")).post("/api/v1/auth/password-reset/request").then().statusCode(429);
    }
    @Test void resetInvalidatesAllSessionsAndCannotBeReused() {
        String first=register(), second=login(email,PASSWORD).cookie("session"), token=resetToken();
        mutateUser(u -> { AuthToken t=AuthToken.find("user",u).firstResult(); assertArrayEquals(Tokens.hash(token),t.tokenHash); });
        confirm(token).then().statusCode(204);
        session(first,401); session(second,401);
        confirm(token).then().statusCode(400).body("code",is("RESET_INVALID"));
        login(email,PASSWORD).then().statusCode(401);
        login(email,NEW_PASSWORD).then().statusCode(204);
    }
    @Test void expiredResetAndNeutralRequest() {
        register(); String token=resetToken();
        QuarkusTransaction.requiringNew().run(() -> AuthToken.update("expiresAt = ?1 where tokenHash = ?2",Instant.now().minusSeconds(1),Tokens.hash(token)));
        confirm(token).then().statusCode(400).body("code",is("RESET_INVALID"));
        confirm(Tokens.random()).then().statusCode(400).body("code",is("RESET_INVALID"));
        request(null).body(Map.of("email","absent@example.test")).post("/api/v1/auth/password-reset/request").then().statusCode(202).body(is(""));
        assertTrue(mailbox.getMailsSentTo("absent@example.test").isEmpty());
        cleanup.clean();
        QuarkusTransaction.requiringNew().run(() -> assertEquals(0,AuthToken.count("tokenHash",Tokens.hash(token))));
    }
    @Test void concurrentResetHasExactlyOneWinner() throws Exception {
        register(); String token=resetToken();
        try (var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> call=() -> { start.await(); return confirm(token).statusCode(); };
            var a=executor.submit(call); var b=executor.submit(call); start.countDown();
            assertEquals(List.of(204,400),java.util.stream.Stream.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)).sorted().toList());
        }
    }
    @Test void englishResetUsesTrustedUrlAndNoSecretsInResponses() {
        var data=registration(); data.put("locale","en");
        String session=request(null).body(data).post("/api/v1/auth/register").then().statusCode(201).body(is("")).extract().cookie("session");
        request(session).header("X-Forwarded-Host","evil.example").body(Map.of("email",email)).post("/api/v1/auth/password-reset/request").then().statusCode(202).body(is(""));
        var mail=mailbox.getMailsSentTo(email).getFirst();
        assertEquals("Reset your password",mail.getSubject());
        assertTrue(mail.getText().contains("http://localhost:3000/en/reset-password#token="));
        assertFalse(mail.getText().contains("evil.example"));
        session(session,200);
    }
    @Test void accountLimitAppliesIndependentlyOfIpLimit() {
        String key="login-account:"+HexFormat.of().formatHex(Tokens.hash(email));
        for (int i=0;i<10;i++) limiter.check(key,10,Duration.ofMinutes(15));
        login(email.toUpperCase(Locale.ROOT),PASSWORD).then().statusCode(429).body("code",is("RATE_LIMITED"));
    }
    @Test void resetMismatchDoesNotConsumeTokenAndBlockedAccountGetsNoMail() {
        String session=register(), token=resetToken();
        request(null).body(Map.of("token",token,"password",NEW_PASSWORD,"passwordConfirmation",PASSWORD))
            .post("/api/v1/auth/password-reset/confirm").then().statusCode(400).body("code",is("PASSWORD_MISMATCH"));
        session(session,200);
        confirm(token).then().statusCode(204);
        mailbox.clear(); mutateUser(u -> u.status="BLOCKED");
        request(null).body(Map.of("email",email)).post("/api/v1/auth/password-reset/request").then().statusCode(202);
        assertTrue(mailbox.getMailsSentTo(email).isEmpty());
    }
    @Test void concurrentRegistrationKeepsEmailUnique() throws Exception {
        try (var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> call=() -> { start.await(); return request(null).body(registration()).post("/api/v1/auth/register").statusCode(); };
            var a=executor.submit(call); var b=executor.submit(call); start.countDown();
            assertEquals(List.of(201,409),java.util.stream.Stream.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)).sorted().toList());
        }
    }
    @Test void cleanupRemovesExpiredSessionsButKeepsValidOnes() {
        String expired=register(), valid=login(email,PASSWORD).cookie("session");
        QuarkusTransaction.requiringNew().run(() -> Session.update("expiresAt = ?1 where tokenHash = ?2",Instant.now().minusSeconds(1),Tokens.hash(expired)));
        cleanup.clean();
        QuarkusTransaction.requiringNew().run(() -> assertEquals(0,Session.count("tokenHash",Tokens.hash(expired))));
        session(valid,200);
    }

    @Test void disabledGoogleNeedsNoCredentials() {
        given().get("/api/v1/auth/google/config").then().statusCode(200).body("enabled",is(false));
        request(null).body(Map.of("mode","login","locale","pl")).post("/api/v1/auth/google/start")
            .then().statusCode(503).body("code",is("GOOGLE_DISABLED"));
        String token=register(); session(token,200);
    }

}
