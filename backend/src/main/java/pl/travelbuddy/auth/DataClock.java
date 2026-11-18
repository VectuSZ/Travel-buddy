package pl.travelbuddy.auth;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.*;
@ApplicationScoped
public class DataClock {
 public Instant now() { return Instant.now(); }
 public LocalDate today() { return now().atZone(ZoneId.of("Europe/Warsaw")).toLocalDate(); }
}
