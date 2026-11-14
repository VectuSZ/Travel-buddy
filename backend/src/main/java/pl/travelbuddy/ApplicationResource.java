package pl.travelbuddy;
import jakarta.ws.rs.*;
import jakarta.inject.Inject;
import pl.travelbuddy.auth.*;
import pl.travelbuddy.profiles.ProfileDtos;
import io.vertx.ext.web.RoutingContext;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
@Path("/api/v1") @Produces("application/json")
public class ApplicationResource {
 @Inject ApplicationService service;
 @Inject SessionService sessions;
 @Inject RoutingContext routing;
 User user(){var c=routing.request().getCookie(sessions.cookieName());return sessions.require(c==null?null:c.getValue());}
 @POST @Path("/trips/{id}/applications") @Consumes("application/json") public ApplicationService.View apply(@PathParam("id") UUID id,JsonNode n){return service.apply(user(),id,n);}
 @GET @Path("/me/applications") public List<ApplicationService.View> mine(){return service.mine(user());}
 @GET @Path("/trips/{id}/applications") public List<ApplicationService.View> incoming(@PathParam("id") UUID id){return service.incoming(user(),id);}
 @POST @Path("/applications/{id}/{action:accept|reject|withdraw}") public ApplicationService.View decide(@PathParam("id") UUID id,@PathParam("action") String action){return service.decide(user(),id,action);}
 @GET @Path("/trips/{id}/participants") public List<ProfileDtos.PublicProfile> participants(@PathParam("id") UUID id){return service.participants(id);}
 @GET @Path("/trips/{id}/contacts/{target}") public ProfileDtos.Contacts contacts(@PathParam("id") UUID id,@PathParam("target") UUID target){return service.contacts(user(),id,target);}
}
