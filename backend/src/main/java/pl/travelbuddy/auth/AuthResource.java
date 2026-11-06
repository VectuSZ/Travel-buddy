package pl.travelbuddy.auth;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
@Path("/api/v1/auth") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {
 @Inject AuthService auth;
 @Inject SessionService sessions;
 @Inject RateLimiter limiter;
 @Inject RoutingContext routing;
 @ConfigProperty(name="auth.rate.login-ip",defaultValue="10") int loginIp;
 @ConfigProperty(name="auth.rate.login-account",defaultValue="10") int loginAccount;
 @ConfigProperty(name="auth.rate.register-ip",defaultValue="5") int registerIp;
 @ConfigProperty(name="auth.rate.reset-ip",defaultValue="10") int resetIp;
 @ConfigProperty(name="auth.rate.reset-account",defaultValue="3") int resetAccount;
 @ConfigProperty(name="auth.rate.login-window",defaultValue="PT15M") Duration loginWindow;
 @ConfigProperty(name="auth.rate.register-window",defaultValue="PT1H") Duration registerWindow;
 @ConfigProperty(name="auth.rate.reset-window",defaultValue="PT1H") Duration resetWindow;
 String ip() { return routing.request().remoteAddress().hostAddress(); }
 String accountKey(String email) { return HexFormat.of().formatHex(Tokens.hash(AuthService.email(email))); }
 String token() { var cookie=routing.request().getCookie(sessions.cookieName()); return cookie==null?null:cookie.getValue(); }
 @GET @Path("/csrf") public Map<String,String> csrf() { return Map.of("token",routing.<String>get("csrf_token")); }
 @POST @Path("/register") public Response register(@NotNull @Valid AuthDtos.Register r) {
  limiter.check("register:"+ip(),registerIp,registerWindow);
  return Response.status(201).cookie(sessions.cookie(auth.register(r))).build();
 }
 @POST @Path("/login") public Response login(@NotNull @Valid AuthDtos.Login r) {
  limiter.check("login-ip:"+ip(),loginIp,loginWindow);
  limiter.check("login-account:"+accountKey(r.email()),loginAccount,loginWindow);
  return Response.noContent().cookie(sessions.cookie(auth.login(r))).build();
 }
 @GET @Path("/session") public AuthDtos.Account session() { return new AuthDtos.Account(sessions.require(token()).email); }
 @POST @Path("/logout") public Response logout() { sessions.revoke(token()); return Response.noContent().cookie(sessions.cookie("")).build(); }
 @POST @Path("/password-reset/request") public Response request(@NotNull @Valid AuthDtos.ResetRequest r) {
  limiter.check("reset-ip:"+ip(),resetIp,resetWindow);
  limiter.check("reset-account:"+accountKey(r.email()),resetAccount,resetWindow);
  auth.requestReset(r.email()); return Response.accepted().build();
 }
 @POST @Path("/password-reset/confirm") public Response confirm(@NotNull @Valid AuthDtos.ResetConfirm r) {
  limiter.check("reset-confirm:"+ip(),10,loginWindow);
  auth.confirmReset(r); return Response.noContent().cookie(sessions.cookie("")).build();
 }
}
