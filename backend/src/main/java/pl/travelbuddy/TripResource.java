package pl.travelbuddy;
import jakarta.ws.rs.*;
import jakarta.inject.Inject;
import java.util.*;
import pl.travelbuddy.auth.*;
import io.vertx.ext.web.RoutingContext;
import com.fasterxml.jackson.databind.JsonNode;
@Path("/api/v1") @Produces("application/json")
public class TripResource {
 @Inject TripService service;
 @Inject SessionService sessions;
 @Inject RoutingContext routing;
 User user(){var c=routing.request().getCookie(sessions.cookieName());return sessions.require(c==null?null:c.getValue());}
 static final String VISIBLE="moderationStatus = 'VISIBLE' and organizerId in (select u.id from User u where u.status = 'ACTIVE')";
 @Inject TripSearch search;
 @GET @Path("/trips") public TripSearch.Results list(@QueryParam("country") String country,@QueryParam("from") String from,@QueryParam("to") String to,@QueryParam("budgetMin") String min,@QueryParam("budgetMax") String max,@QueryParam("style") String style,@QueryParam("page") String page,@QueryParam("size") String size){return search.search(country,from,to,min,max,style,page,size);}
 @GET @Path("/trips/{id}") public TripView get(@PathParam("id") UUID id){
  Trip t=Trip.find("id = ?1 and status in ('OPEN','CLOSED') and "+VISIBLE,id).firstResult();
  if(t==null)throw new NotFoundException();return service.view(t);
 }
 @GET @Path("/me/trips") public List<TripView> mine(){return service.mine(user());}
 @GET @Path("/me/trips/{id}") public TripView mine(@PathParam("id") UUID id){return service.mine(user(),id);}
 @POST @Path("/trips") @Consumes("application/json") public jakarta.ws.rs.core.Response create(JsonNode n){return jakarta.ws.rs.core.Response.status(201).entity(service.create(user(),n)).build();}
 @PATCH @Path("/trips/{id}") @Consumes("application/json") public TripView patch(@PathParam("id") UUID id,JsonNode n){return service.patch(user(),id,n);}
 @POST @Path("/trips/{id}/publish") @Consumes("application/json") public TripView publish(@PathParam("id") UUID id,JsonNode n){return service.transition(user(),id,n,true);}
 @POST @Path("/trips/{id}/close") @Consumes("application/json") public TripView close(@PathParam("id") UUID id,JsonNode n){return service.transition(user(),id,n,false);}
}
