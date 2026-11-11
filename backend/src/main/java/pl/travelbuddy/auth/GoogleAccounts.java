package pl.travelbuddy.auth;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
@ApplicationScoped
public class GoogleAccounts {
 @Inject SessionService sessions;
 @Transactional public String login(String issuer, String subject, String email, boolean verified, GoogleAttempts.Attempt attempt) {
  // Serialize identity creation across callbacks without storing provider tokens.
  User.getEntityManager().createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key, 0))",Object.class)
   .setParameter("key",issuer+"|"+subject).getSingleResult();
  AuthIdentity identity=AuthIdentity.find("issuer = ?1 and subject = ?2",issuer,subject).firstResult();
  if(identity!=null) {
   if(!"ACTIVE".equals(identity.user.status)) throw new AuthProblem(403,"GOOGLE_ACCOUNT_BLOCKED");
   return sessions.create(identity.user);
  }
  if(!verified || email==null || email.length()>254 || email.chars().anyMatch(Character::isWhitespace) || !email.matches("[^ @]+@[^ @]+[.][^ @]+")) throw new AuthProblem(400,"GOOGLE_EMAIL_UNVERIFIED");
  if(User.count("lower(email) = ?1",AuthService.email(email))>0) throw new AuthProblem(409,"GOOGLE_EMAIL_CONFLICT");
  if(attempt.terms()==null) throw new AuthProblem(400,"GOOGLE_REGISTRATION_REQUIRED");
  User u=new User(); u.id=UUID.randomUUID(); u.email=AuthService.email(email); u.role="USER"; u.status="ACTIVE";
  u.uiLocale=attempt.locale(); u.termsVersion=attempt.terms(); u.termsAcceptedAt=attempt.acceptedAt();
  u.createdAt=Instant.now(); u.updatedAt=u.createdAt; u.emailVerifiedAt=u.createdAt;
  u.persistAndFlush();
  AuthIdentity i=new AuthIdentity(); i.id=UUID.randomUUID(); i.user=u; i.provider="google"; i.issuer=issuer; i.subject=subject; i.createdAt=Instant.now(); i.persistAndFlush();
  return sessions.create(u);
 }
}
