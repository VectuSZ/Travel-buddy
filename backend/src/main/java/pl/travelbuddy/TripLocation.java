package pl.travelbuddy;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import java.util.UUID;
@Entity @Table(name="trip_locations")
public class TripLocation extends PanacheEntityBase {
 @Id public UUID id;
 @Column(name="trip_id") public UUID tripId;
 public short position;
 @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR) @Column(name="country_code",columnDefinition="char(2)") public String countryCode;
 public String city;
}
