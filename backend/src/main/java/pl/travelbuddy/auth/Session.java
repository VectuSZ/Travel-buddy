package pl.travelbuddy.auth;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="sessions")
public class Session extends PanacheEntityBase {
 @Id public UUID id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="user_id") public User user;
 @Column(name="token_hash",nullable=false,columnDefinition="bytea") public byte[] tokenHash;
 @Column(name="created_at",nullable=false) public Instant createdAt;
 @Column(name="expires_at",nullable=false) public Instant expiresAt;
 @Column(name="authenticated_at",nullable=false) public Instant authenticatedAt;
}
