package pl.travelbuddy.auth;
import io.quarkus.security.Authenticated;
import io.quarkus.oidc.IdToken;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
@Path("/api/v1/auth/google") @Produces(MediaType.APPLICATION_JSON)
public class GoogleResource {
 @Inject GoogleAttempts attempts;
 @Inject AccountDeletion deletion;
 @Inject GoogleAccounts accounts;
 @Inject SessionService sessions;
 @Inject RateLimiter limiter;
 @Inject RoutingContext routing;
 @Inject @IdToken JsonWebToken idToken;
 @ConfigProperty(name="auth.google.enabled") boolean enabled;
 @ConfigProperty(name="auth.cookie-secure") boolean secure;
 @ConfigProperty(name="auth.frontend-url") String frontend;
 public record Start(boolean reauthenticate, boolean link,
  @NotNull @Pattern(regexp="pl|en") String locale, String returnUrl) {}
 @GET @Path("/config") public Map<String,Boolean> config() { return Map.of("enabled",enabled); }
 @POST @Path("/start") @Consumes(MediaType.APPLICATION_JSON)
 public Response start(@NotNull @Valid Start input) {
  if(!enabled) throw new AuthProblem(503,"GOOGLE_DISABLED");
  limiter.check("google:"+routing.request().remoteAddress().hostAddress(),10,Duration.ofMinutes(15));
  if(input.returnUrl()!=null || routing.request().getParam("returnUrl")!=null) throw new AuthProblem(400,"VALIDATION_FAILED");
  String token=null;
  if(input.reauthenticate() && input.link()) throw new AuthProblem(400,"VALIDATION_FAILED");
  if(input.reauthenticate() || input.link()) {
   var cookie=routing.request().getCookie(sessions.cookieName());
   token=cookie==null?null:cookie.getValue(); sessions.require(token);
  }
  String flow=attempts.start(input.locale(),token,input.link());
  return Response.ok(Map.of("url","/api/v1/auth/google/authorize")).cookie(flowCookie(flow,600)).build();
 }
 @GET @Path("/authorize") @Authenticated public Response authorize() { return result("pl","GOOGLE_FLOW_INVALID"); }
 @GET @Path("/callback") @Authenticated public Response callback(@QueryParam("state") String state) {
  String locale="pl";
  try {
   var cookie=routing.request().getCookie("google-flow");
   var attempt=attempts.consume(cookie==null?null:cookie.getValue(),state); locale=attempt.locale();
   String sub=idToken.getSubject(), issuer=idToken.getIssuer();
   if(sub==null || sub.isBlank() || sub.length()>255 || issuer==null || issuer.length()>255) throw new AuthProblem(400,"GOOGLE_FAILED");
   if(attempt.sessionToken()!=null && !attempt.linking()) {
    Object authenticated=idToken.getClaim("auth_time");
    if(!(authenticated instanceof Number) || ((Number)authenticated).longValue()<attempt.startedAt().getEpochSecond()
       || ((Number)authenticated).longValue()>java.time.Instant.now().plusSeconds(30).getEpochSecond())
     throw new AuthProblem(403,"REAUTHENTICATION_REQUIRED");
    var current=routing.request().getCookie(sessions.cookieName());
    if(current==null || !current.getValue().equals(attempt.sessionToken())) throw new AuthProblem(403,"GOOGLE_FLOW_INVALID");
    deletion.confirmGoogle(attempt.sessionToken(),issuer,sub); clearOidc();
    return Response.seeOther(URI.create(frontend+(locale.equals("en")?"/en":"")+"/account")).cookie(flowCookie("",0)).build();
   }
   String email=idToken.getClaim("email");
   boolean verified=Boolean.TRUE.equals(idToken.getClaim("email_verified"));
   String session=null;
   GoogleAttempts.LinkTarget target=null;
   if(attempt.linking()) {
    if(!attempt.sessionToken().equals(currentSession())) throw new AuthProblem(403,"GOOGLE_FLOW_INVALID");
    if(!verified || email==null || email.isBlank() || email.length()>254) throw new AuthProblem(400,"GOOGLE_EMAIL_UNVERIFIED");
    target=accounts.target(email,issuer,sub,currentSession());
   } else {
    try { session=accounts.login(issuer,sub,email,verified,locale,false); }
    catch(AuthProblem e) {
     if(!"GOOGLE_EMAIL_CONFLICT".equals(e.code)) throw e;
     target=accounts.target(email,issuer,sub,null);
    }
   }
   if(session==null) {
    attempts.awaitConsent(attempt,issuer,sub,email,target);
    clearOidc();
    String path=target==null?"/google-terms":"/google-link";
    return Response.seeOther(URI.create(frontend+(locale.equals("en")?"/en":"")+path)).build();
   }
   clearOidc();
   return Response.seeOther(URI.create(frontend+(locale.equals("en")?"/en":"")+"/account")).cookie(sessions.cookie(session),flowCookie("",0)).build();
  } catch(AuthProblem e) { return result(locale,e.code); }
  catch(org.hibernate.exception.ConstraintViolationException e) { return result(locale,"GOOGLE_EMAIL_CONFLICT"); }
  catch(RuntimeException e) { return result(locale,"GOOGLE_FAILED"); }
 }
 public record Consent(@NotNull String confirmation, @NotNull Boolean accepted, String termsVersion) {}
 @GET @Path("/consent") public Map<String,String> consent(@CookieParam("google-flow") String flow) {
  var r=enabled?attempts.pendingDecision(flow,false):null;
  return Map.of("confirmation",r==null?"":r.confirmation());
 }
 @POST @Path("/consent") @Consumes(MediaType.APPLICATION_JSON)
 public Response decide(@CookieParam("google-flow") String flow,@NotNull @Valid Consent input) {
  if(!enabled) throw new AuthProblem(503,"GOOGLE_DISABLED");
  if(input.accepted() && !AuthDtos.TERMS_VERSION.equals(input.termsVersion())) throw new AuthProblem(400,"GOOGLE_TERMS_REQUIRED");
  var registration=attempts.consumeRegistration(flow,input.confirmation(),false);
  var response=Response.noContent().cookie(flowCookie("",0));
  if(input.accepted()) {
   try {
    String session=accounts.login(registration.issuer(),registration.subject(),registration.email(),true,registration.attempt().locale(),true);
    response.cookie(sessions.cookie(session));
   } catch(org.hibernate.exception.ConstraintViolationException e) { throw new AuthProblem(409,"GOOGLE_EMAIL_CONFLICT"); }
  }
  return response.build();
 }
 String currentSession() {
  var cookie=routing.request().getCookie(sessions.cookieName());
  return cookie==null?null:cookie.getValue();
 }
 @GET @Path("/methods") public GoogleAccounts.Methods methods() { return accounts.methods(currentSession(),enabled); }
 @GET @Path("/link") public Map<String,Object> linkInfo(@CookieParam("google-flow") String flow) {
  var r=enabled?attempts.pendingDecision(flow,true):null;
  if(r==null) return Map.of();
  return Map.of("confirmation",r.confirmation(),"email",r.email(),"settings",r.attempt().linking());
 }
 public record LinkDecision(@NotNull String confirmation,boolean cancel,@Size(max=1024) String password) {}
 @POST @Path("/link") @Consumes(MediaType.APPLICATION_JSON)
 public Response link(@CookieParam("google-flow") String flow,@NotNull @Valid LinkDecision input) {
  if(!enabled) throw new AuthProblem(503,"GOOGLE_DISABLED");
  var response=Response.noContent().cookie(flowCookie("",0));
  if(input.cancel()) {
   attempts.consumeRegistration(flow,input.confirmation(),true);
   return response.build();
  }
  var r=attempts.pendingDecision(flow,true);
  if(r==null || !r.confirmation().equals(input.confirmation())) throw new AuthProblem(400,"GOOGLE_FLOW_INVALID");
  limiter.check("google-link-ip:"+routing.request().remoteAddress().hostAddress(),10,Duration.ofMinutes(15));
  limiter.check("google-link-account:"+r.target().userId(),10,Duration.ofMinutes(15));
  if(input.password()==null || input.password().isEmpty()) throw new AuthProblem(401,"INVALID_CREDENTIALS");
  try { return response.cookie(sessions.cookie(accounts.link(flow,r,input.password(),currentSession()))).build(); }
  catch(org.hibernate.exception.ConstraintViolationException e) { throw new AuthProblem(409,"GOOGLE_LINK_UNAVAILABLE"); }
 }
 @GET @Path("/error") public Response error(@QueryParam("error") String error) {
  var cookie=routing.request().getCookie("google-flow");
  return result(attempts.locale(cookie==null?null:cookie.getValue()),"access_denied".equals(error)?"GOOGLE_CANCELLED":"GOOGLE_FAILED");
 }
 Response result(String locale,String code) {
  clearOidc();
  return Response.seeOther(URI.create(frontend+(locale.equals("en")?"/en":"")+"/google-result?code="+code)).cookie(flowCookie("",0)).build();
 }
 void clearOidc() { routing.response().removeCookie("q_session",true); }
 NewCookie flowCookie(String value,int age) { return new NewCookie.Builder("google-flow").value(value).path("/").httpOnly(true).secure(secure).sameSite(NewCookie.SameSite.LAX).maxAge(age).build(); }
}
