package pl.travelbuddy;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="trips")
public class Trip extends PanacheEntityBase {
 @Id public UUID id;
 @NotBlank @Column(length=200,nullable=false) public String title;
 @NotBlank @Column(length=100,nullable=false) public String country;
 @NotBlank @Column(columnDefinition="text",nullable=false) public String description;
 @NotNull @Column(name="start_date",nullable=false) public LocalDate startDate;
 @NotNull @Column(name="end_date",nullable=false) public LocalDate endDate;
 @Pattern(regexp="pl|en") @Column(name="content_language",length=2,nullable=false) public String contentLanguage;
}
