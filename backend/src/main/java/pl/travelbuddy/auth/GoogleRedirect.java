package pl.travelbuddy.auth;
import io.quarkus.oidc.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.*;
import java.nio.charset.StandardCharsets;
@jakarta.inject.Singleton @io.quarkus.arc.Unremovable @Redirect(Redirect.Location.OIDC_AUTHORIZATION)
public class GoogleRedirect implements OidcRedirectFilter {
 @Inject GoogleAttempts attempts;
 @Override public void filter(OidcRedirectContext c) {
  String state=null;
  for(String part:URI.create(c.redirectUri()).getRawQuery().split("&")) {
   String[] kv=part.split("=",2);
   if(kv[0].equals("state")) state=URLDecoder.decode(kv[1],StandardCharsets.UTF_8);
  }
  var cookie=c.routingContext().request().getCookie("google-flow");
  if(state==null || cookie==null) throw new AuthProblem(400,"GOOGLE_FLOW_INVALID");
  attempts.bind(cookie.getValue(),state);
 }
}
