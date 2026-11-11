package pl.travelbuddy.auth;
import io.quarkus.vertx.http.runtime.filters.Filters;
import jakarta.enterprise.event.Observes;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
/** Includes failures produced by the security layer before REST response filters run. */
@ApplicationScoped
public class GoogleHttpHeaders {
 @ConfigProperty(name="auth.frontend-url") String frontend;
 @jakarta.inject.Inject GoogleAttempts attempts;
 void register(@Observes Filters filters) { filters.register(this::headers, 1000); }
 void headers(RoutingContext context) {
  if(context.request().path().startsWith("/api/v1/auth/google/")) {
   context.addHeadersEndHandler(ignored -> {
    context.response().putHeader("Cache-Control","no-store").putHeader("Referrer-Policy","no-referrer");
    if(context.request().method()==io.vertx.core.http.HttpMethod.GET && context.response().getStatusCode()>=400) {
     var cookie=context.request().getCookie("google-flow");
     String locale=attempts.locale(cookie==null?null:cookie.getValue());
     context.response().setStatusCode(303).putHeader("Location",frontend+(locale.equals("en")?"/en":"")+"/google-result?code=GOOGLE_FAILED");
    }
   });
  }
  context.next();
 }
}
