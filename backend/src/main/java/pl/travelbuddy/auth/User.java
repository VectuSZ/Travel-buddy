package pl.travelbuddy.auth;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="users")
public class User extends PanacheEntityBase {
 @Id public UUID id;
 @Column(nullable=false,length=254) public String email;
 @Column(name="password_hash",columnDefinition="text") public String passwordHash;
 @Column(name="email_verified_at") public Instant emailVerifiedAt;
 @Column(nullable=false,length=16) public String role;
 @Column(nullable=false,length=24) public String status;
 @Column(name="ui_locale",nullable=false,length=5) public String uiLocale;
 @Column(name="terms_version",nullable=false,length=32) public String termsVersion;
 @Column(name="terms_accepted_at",nullable=false) public Instant termsAcceptedAt;
 @Column(name="created_at",nullable=false) public Instant createdAt;
 @Column(name="updated_at",nullable=false) public Instant updatedAt;
}
