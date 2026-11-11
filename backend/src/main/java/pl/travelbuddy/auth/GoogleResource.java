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
 @Inject GoogleAccounts accounts;
 @Inject SessionService sessions;
 @Inject RateLimiter limiter;
 @Inject RoutingContext routing;
 @Inject @IdToken JsonWebToken idToken;
 @ConfigProperty(name="auth.google.enabled") boolean enabled;
 @ConfigProperty(name="auth.cookie-secure") boolean secure;
 @ConfigProperty(name="auth.frontend-url") String frontend;
 public record Start(@NotNull @Pattern(regexp="login|register") String mode,
  @NotNull @Pattern(regexp="pl|en") String locale, boolean acceptTerms, String termsVersion, String returnUrl) {}
 @GET @Path("/config") public Map<String,Boolean> config() { return Map.of("enabled",enabled); }
 @POST @Path("/start") @Consumes(MediaType.APPLICATION_JSON)
 public Response start(@NotNull @Valid Start input) {
  if(!enabled) throw new AuthProblem(503,"GOOGLE_DISABLED");
  limiter.check("google:"+routing.request().remoteAddress().hostAddress(),10,Duration.ofMinutes(15));
  boolean register=input.mode().equals("register");
  if(register && (!input.acceptTerms() || !"local-draft-1".equals(input.termsVersion()))) throw new AuthProblem(400,"GOOGLE_TERMS_REQUIRED");
  if(input.returnUrl()!=null || routing.request().getParam("returnUrl")!=null) throw new AuthProblem(400,"VALIDATION_FAILED");
  String flow=attempts.start(input.locale(),register?"local-draft-1":null);
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
   String session=accounts.login(issuer,sub,idToken.getClaim("email"),Boolean.TRUE.equals(idToken.getClaim("email_verified")),attempt);
   clearOidc();
   return Response.seeOther(URI.create(frontend+(locale.equals("en")?"/en":"")+"/account")).cookie(sessions.cookie(session),flowCookie("",0)).build();
  } catch(AuthProblem e) { return result(locale,e.code); }
  catch(org.hibernate.exception.ConstraintViolationException e) { return result(locale,"GOOGLE_EMAIL_CONFLICT"); }
  catch(RuntimeException e) { return result(locale,"GOOGLE_FAILED"); }
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
