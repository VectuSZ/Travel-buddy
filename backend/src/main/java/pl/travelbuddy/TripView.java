package pl.travelbuddy;
import java.util.*;
import pl.travelbuddy.profiles.ProfileDtos;
// Explicit allowlist, shared trip content contains no account or contact data.
public record TripView(UUID id, String slug, String title, String country, String description,
 java.time.LocalDate startDate, java.time.LocalDate endDate, String contentLanguage,
 String itinerary, String lookingFor, String dateMode, java.time.LocalDate dateFrom,
 java.time.LocalDate dateTo, Short durationDays, Short targetPeople,
 java.math.BigDecimal budgetMin, java.math.BigDecimal budgetMax, String currency,
 String[] styles, String status, int version, List<Location> locations,
 ProfileDtos.PublicProfile organizer) {
 public record Location(String countryCode,String city) {}
 static TripView of(Trip t, ProfileDtos.PublicProfile organizer) {
  return new TripView(t.id,TripSearch.slug(t.title),t.title,t.country,t.itinerary,t.dateFrom,t.dateTo,t.contentLanguage,
   t.itinerary,t.lookingFor,t.dateMode,t.dateFrom,t.dateTo,t.durationDays,t.targetPeople,
   t.budgetMin,t.budgetMax,t.currency,t.styles,t.status,t.version,
   TripLocation.<TripLocation>list("tripId = ?1 order by position",t.id).stream().map(l->new Location(l.countryCode,l.city)).toList(),organizer);
 }
}
