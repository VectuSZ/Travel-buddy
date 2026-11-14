package pl.travelbuddy;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import java.util.UUID;
import java.time.Instant;
@Entity @Table(name="applications")
public class TripApplication extends PanacheEntityBase {
 @Id public UUID id;
 @Column(name="trip_id") public UUID tripId;
 @Column(name="applicant_id") public UUID applicantId;
 public String message;
 public String status;
 @Column(name="decided_at") public Instant decidedAt;
 @Column(name="created_at") public Instant createdAt;
 @Column(name="updated_at") public Instant updatedAt;
}
