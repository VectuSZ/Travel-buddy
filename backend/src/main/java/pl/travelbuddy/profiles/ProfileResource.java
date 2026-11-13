package pl.travelbuddy.profiles;
import pl.travelbuddy.auth.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
@Path("/api/v1") @Produces("application/json")
public class ProfileResource {
 @Inject SessionService sessions;
 @Inject ProfileService profiles;
 @Inject RoutingContext routing;
 User user() { var c=routing.request().getCookie(sessions.cookieName()); return sessions.require(c==null?null:c.getValue()); }
 @GET @Path("/me") public ProfileDtos.Me me() { return profiles.me(user()); }
 @PATCH @Path("/me/profile") @Consumes("application/json") public ProfileDtos.Me patch(JsonNode input) { return profiles.patch(user(),input); }
 @PUT @Path("/me/contacts") @Consumes("application/json") public ProfileDtos.Me contacts(JsonNode input) { return profiles.contacts(user(),input); }
 @GET @Path("/profiles/{id}") public ProfileDtos.PublicProfile get(@PathParam("id") UUID id) { return profiles.publicProfile(id); }
}
