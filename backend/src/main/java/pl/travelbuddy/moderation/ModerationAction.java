package pl.travelbuddy.moderation;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import java.util.UUID;
import java.time.Instant;
@Entity @Table(name="moderation_actions")
public class ModerationAction extends PanacheEntityBase {
 @Id public UUID id;
 @Column(name="admin_id") public UUID adminId;
 @Column(name="report_id") public UUID reportId;
 @Column(name="target_trip_id") public UUID targetTripId;
 @Column(name="target_user_id") public UUID targetUserId;
 @Column(name="action") public String action;
 @Column(name="reason") public String reason;
 @Column(name="created_at") public Instant createdAt;
}
