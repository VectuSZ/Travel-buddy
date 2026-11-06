package pl.travelbuddy.auth;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.*;
@Provider
public class Problems implements ExceptionMapper<AuthProblem> {
 public record Body(String type, int status, String code) {}
 public static Response response(int status, String code) {
  return Response.status(status).type("application/problem+json").header("Cache-Control","no-store")
   .entity(new Body("about:blank",status,code)).build();
 }
 @Override public Response toResponse(AuthProblem e) { return response(e.status,e.code); }
}
