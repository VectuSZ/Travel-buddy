package pl.travelbuddy.auth;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.ext.Provider;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import org.eclipse.microprofile.config.inject.ConfigProperty;
@Provider @Priority(Priorities.AUTHENTICATION - 100)
public class AuthFilters implements ContainerRequestFilter, ContainerResponseFilter {
 @ConfigProperty(name="auth.frontend-url") String origin;
 private boolean auth(ContainerRequestContext r) { String p=r.getUriInfo().getPath().replaceFirst("^/", ""); return p.startsWith("api/v1/auth/") || p.equals("api/v1/me") || p.startsWith("api/v1/me/") || p.startsWith("api/v1/profiles/"); }
 @Override public void filter(ContainerRequestContext r) {
  if (auth(r) && !java.util.Set.of("GET","HEAD","OPTIONS").contains(r.getMethod())) {
   if (!origin.equals(r.getHeaderString("Origin"))) { r.abortWith(Problems.response(403,"ORIGIN_REJECTED")); return; }
   if (!Tokens.valid(r.getHeaderString("X-CSRF-TOKEN"))) r.abortWith(Problems.response(403,"CSRF_INVALID"));
  }
 }
 @Override public void filter(ContainerRequestContext r, ContainerResponseContext s) {
  if (!auth(r)) return;
  s.getHeaders().putSingle("Cache-Control", r.getUriInfo().getPath().contains("/auth/") ? "no-store" : "private, no-store");
  s.getHeaders().putSingle("Referrer-Policy","no-referrer");
  if (s.getStatus()>=400 && !(s.getEntity() instanceof Problems.Body)) {
   String code=s.getStatus()>=500 ? "INTERNAL_ERROR"
    : s.getStatus()==400 && !s.hasEntity() ? "CSRF_INVALID" : "VALIDATION_FAILED";
   if (code.equals("CSRF_INVALID")) s.setStatus(403);
   s.setEntity(new Problems.Body("about:blank",s.getStatus(),code));
   s.getHeaders().putSingle("Content-Type","application/problem+json");
  }
 }
}
