package pl.travelbuddy;
import java.time.LocalDate;
import java.util.UUID;
public record TripDto(UUID id, String title, String country, String description, LocalDate startDate, LocalDate endDate, String contentLanguage) {
 static TripDto from(Trip t) { return new TripDto(t.id,t.title,t.country,t.description,t.startDate,t.endDate,t.contentLanguage); }
}
