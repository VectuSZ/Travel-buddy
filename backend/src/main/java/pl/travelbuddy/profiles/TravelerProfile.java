package pl.travelbuddy.profiles;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="traveler_profiles")
public class TravelerProfile extends PanacheEntityBase {
 @Id @Column(name="user_id") public UUID userId;
 @Column(name="display_name",nullable=false,length=60) public String displayName;
 @Column(nullable=false) public short age;
 @Column(name="age_updated_at",nullable=false) public LocalDate ageUpdatedAt;
 @Column(length=1000) public String bio;
 @Column(nullable=false,columnDefinition="text[]") public String[] languages;
 @Column(name="travel_styles",nullable=false,columnDefinition="text[]") public String[] travelStyles;
 @Version public int version;
 @Column(name="created_at",nullable=false) public Instant createdAt;
 @Column(name="updated_at",nullable=false) public Instant updatedAt;
}
