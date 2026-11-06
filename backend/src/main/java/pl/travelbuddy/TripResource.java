package pl.travelbuddy;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.*;
@Path("/api/v1/trips") @Produces(MediaType.APPLICATION_JSON)
public class TripResource {
 @GET public List<TripDto> list() { return Trip.<Trip>list("order by startDate, id").stream().map(TripDto::from).toList(); }
 @GET @Path("/{id}") public TripDto get(@PathParam("id") UUID id) {
 Trip trip = Trip.findById(id);
 if (trip == null) throw new NotFoundException();
 return TripDto.from(trip);
 }
}
