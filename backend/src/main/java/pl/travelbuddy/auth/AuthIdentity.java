package pl.travelbuddy.auth;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="auth_identities")
public class AuthIdentity extends PanacheEntityBase {
 @Id public UUID id;
 @ManyToOne(optional=false) @JoinColumn(name="user_id") public User user;
 @Column(nullable=false,length=32) public String provider;
 @Column(nullable=false) public String issuer;
 @Column(nullable=false) public String subject;
 @Column(name="created_at",nullable=false) public Instant createdAt;
}
