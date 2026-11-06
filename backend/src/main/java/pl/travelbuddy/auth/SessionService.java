package pl.travelbuddy.auth;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.persistence.LockModeType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.ws.rs.core.NewCookie;
import java.time.*;
import java.util.UUID;
@ApplicationScoped
public class SessionService {
 @ConfigProperty(name="auth.cookie-name") String cookieName;
 @ConfigProperty(name="auth.cookie-secure") boolean secure;
 // Caller must have authenticated the user. Lock also serializes login against password reset.
 @Transactional public String create(User user) {
  User.getEntityManager().refresh(user,LockModeType.PESSIMISTIC_WRITE);
  if (!"ACTIVE".equals(user.status)) throw new AuthProblem(401,"INVALID_CREDENTIALS");
  String token=Tokens.random();
  Session s=new Session(); s.id=UUID.randomUUID(); s.user=user; s.tokenHash=Tokens.hash(token);
  s.createdAt=Instant.now(); s.authenticatedAt=s.createdAt; s.expiresAt=s.createdAt.plus(Duration.ofDays(7)); s.persist();
  return token;
 }
 public User require(String token) {
  if (!Tokens.valid(token)) throw new AuthProblem(401,"UNAUTHENTICATED");
  Session s=Session.find("tokenHash = ?1 and expiresAt > ?2",Tokens.hash(token),Instant.now()).firstResult();
  if (s == null || !"ACTIVE".equals(s.user.status)) throw new AuthProblem(401,"UNAUTHENTICATED");
  return s.user;
 }
 @Transactional public void revoke(String token) { if (Tokens.valid(token)) Session.delete("tokenHash",Tokens.hash(token)); }
 public String cookieName() { return cookieName; }
 public NewCookie cookie(String token) {
  return new NewCookie.Builder(cookieName).value(token).path("/").httpOnly(true).secure(secure)
   .sameSite(NewCookie.SameSite.LAX).maxAge(token.isEmpty()?0:604800).build();
 }
}
