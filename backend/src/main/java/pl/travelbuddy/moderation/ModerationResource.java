package pl.travelbuddy.moderation;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import io.vertx.ext.web.RoutingContext;
import pl.travelbuddy.auth.*;
import java.util.*;
import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;
@Path("/api/v1") @Produces("application/json")
public class ModerationResource {
 @Inject SessionService sessions;
 @Inject RoutingContext routing;
 @Inject ModerationService service;
 @Inject ReportChallenge challenge;
 @Inject RateLimiter limiter;
 User user(boolean required){var cookie=routing.request().getCookie(sessions.cookieName());return cookie==null&&!required?null:sessions.require(cookie==null?null:cookie.getValue());}
 @GET @Path("/reports/config") public Map<String,Object> config(){return challenge.config();}
 @POST @Path("/reports/challenge") public Map<String,String> token(){limiter.check("report-token:"+routing.request().remoteAddress().hostAddress(),20,Duration.ofHours(1));return Map.of("token",challenge.issue());}
 @POST @Path("/reports") @Consumes("application/json") public Map<String,UUID> report(JsonNode n){User u=user(false);limiter.check("report:"+routing.request().remoteAddress().hostAddress(),5,Duration.ofHours(1));return Map.of("id",service.report(u,n));}
 @GET @Path("/admin/access") public Map<String,Boolean> access(){service.admin(user(true));return Map.of("admin",true);}
 @GET @Path("/admin/reports") public List<ModerationService.View> list(@QueryParam("status") @DefaultValue("OPEN") String status){return service.list(user(true),status);}
 @POST @Path("/admin/reports/{id}/resolve") @Consumes("application/json") public ModerationService.View resolve(@PathParam("id") UUID id,JsonNode n){return service.resolve(user(true),id,n);}
 @POST @Path("/admin/trips/{id}/{action:hide|unhide}") @Consumes("application/json") public void trip(@PathParam("id") UUID id,@PathParam("action") String action,JsonNode n){service.act(user(true),id,action,n);}
 @POST @Path("/admin/users/{id}/{action:block|unblock}") @Consumes("application/json") public void account(@PathParam("id") UUID id,@PathParam("action") String action,JsonNode n){service.act(user(true),id,action,n);}
}
