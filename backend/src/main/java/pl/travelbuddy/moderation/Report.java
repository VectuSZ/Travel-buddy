package pl.travelbuddy.moderation;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import java.util.UUID;
import java.time.Instant;
@Entity @Table(name="reports")
public class Report extends PanacheEntityBase {
 @Id public UUID id;
 @Column(name="reporter_id") public UUID reporterId;
 @Column(name="target_trip_id") public UUID targetTripId;
 @Column(name="target_user_id") public UUID targetUserId;
 @Column(name="resolved_by") public UUID resolvedBy;
 @Column(name="reporter_name") public String reporterName;
 @Column(name="reporter_email") public String reporterEmail;
 @Column(name="target_url") public String targetUrl;
 @Column(name="category") public String category;
 @Column(name="description") public String description;
 @Column(name="status") public String status;
 @Column(name="resolution_reason") public String resolutionReason;
 @Column(name="good_faith") public boolean goodFaith;
 @Column(name="resolved_at") public Instant resolvedAt;
 @Column(name="created_at") public Instant createdAt;
 @Column(name="updated_at") public Instant updatedAt;
}
