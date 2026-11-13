package pl.travelbuddy.profiles;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="profile_contacts")
public class ProfileContacts extends PanacheEntityBase {
 @Id @Column(name="user_id") public UUID userId;
 @Column(name="contact_email",length=254) public String contactEmail;
 @Column(name="instagram_handle",length=30) public String instagramHandle;
 @Column(name="whatsapp_e164",length=16) public String whatsappE164;
 @Column(name="updated_at",nullable=false) public Instant updatedAt;
}
