package pl.travelbuddy;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="trips")
public class Trip extends PanacheEntityBase {
 @Id public UUID id;
 @Column(length=200,nullable=true) public String title;
 @Column(length=100,nullable=true) public String country;
 @Column(columnDefinition="text",nullable=true) public String description;
 @Column(name="start_date",nullable=true) public LocalDate startDate;
 @Column(name="end_date",nullable=true) public LocalDate endDate;
 @Column(name="content_language",length=35,nullable=true) public String contentLanguage;
 @Column(name="organizer_id") public UUID organizerId;
 @Column(name="slug") public String slug;
 @Column(name="itinerary") public String itinerary;
 @Column(name="looking_for") public String lookingFor;
 @Column(name="date_mode") public String dateMode;
 @Column(name="status") public String status;
 @Column(name="moderation_status") public String moderationStatus;
 @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR) @Column(name="currency",columnDefinition="char(3)") public String currency;
 @Column(name="date_from") public LocalDate dateFrom;
 @Column(name="date_to") public LocalDate dateTo;
 @Column(name="duration_days") public Short durationDays;
 @Column(name="target_people") public Short targetPeople;
 @Column(name="budget_min") public java.math.BigDecimal budgetMin;
 @Column(name="budget_max") public java.math.BigDecimal budgetMax;
 @Column(name="published_at") public java.time.Instant publishedAt;
 @Column(name="closed_at") public java.time.Instant closedAt;
 @Column(name="created_at") public java.time.Instant createdAt;
 @Column(name="updated_at") public java.time.Instant updatedAt;
 @Column(name="first_accepted_at") public java.time.Instant firstAcceptedAt;
 @Version public int version;
 @Column(columnDefinition="text[]") public String[] styles = {};
}
