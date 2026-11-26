package pl.travelbuddy.auth;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
@ApplicationScoped
public class GoogleAccounts {
 @Inject SessionService sessions;
 @Inject Passwords passwords;
 @Inject GoogleAttempts attempts;
 @Transactional public String login(String issuer, String subject, String email, boolean verified, String locale, boolean accepted) {
  // Serialize identity creation across callbacks without storing provider tokens.
  lockIdentity(issuer,subject);
  AuthIdentity identity=AuthIdentity.find("issuer = ?1 and subject = ?2",issuer,subject).firstResult();
  if(identity!=null) {
   if(!"ACTIVE".equals(identity.user.status)) throw new AuthProblem(403,"GOOGLE_ACCOUNT_BLOCKED");
   return sessions.create(identity.user);
  }
  if(!verified || email==null || email.length()>254 || email.chars().anyMatch(Character::isWhitespace) || !email.matches("[^ @]+@[^ @]+[.][^ @]+")) throw new AuthProblem(400,"GOOGLE_EMAIL_UNVERIFIED");
  if(User.count("lower(email) = ?1",AuthService.email(email))>0) throw new AuthProblem(409,"GOOGLE_EMAIL_CONFLICT");
  if(!accepted) return null;
  User u=new User(); u.id=UUID.randomUUID(); u.email=AuthService.email(email); u.role="USER"; u.status="ACTIVE";
  u.uiLocale=locale; u.termsVersion=AuthDtos.TERMS_VERSION; u.termsAcceptedAt=Instant.now();
  u.createdAt=Instant.now(); u.updatedAt=u.createdAt; u.emailVerifiedAt=u.createdAt;
  u.persistAndFlush();
  attach(u,issuer,subject);
  return sessions.create(u);
 }

 private void lockIdentity(String issuer,String subject) {
  User.getEntityManager().createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key, 0))",Object.class)
   .setParameter("key",issuer+"|"+subject).getSingleResult();
 }
 private void attach(User u,String issuer,String subject) {
  AuthIdentity i=new AuthIdentity(); i.id=UUID.randomUUID(); i.user=u; i.provider="google"; i.issuer=issuer; i.subject=subject; i.createdAt=Instant.now(); i.persistAndFlush();
 }
 private boolean hasGoogle(User u) { return AuthIdentity.count("user = ?1 and provider = 'google'",u)>0; }
 public record Methods(boolean password,boolean google,boolean enabled) {}
 public Methods methods(String token,boolean enabled) {
  User u=sessions.require(token);
  return new Methods(u.passwordHash!=null,hasGoogle(u),enabled);
 }
 private String revision(User u) { return java.util.HexFormat.of().formatHex(Tokens.hash(u.passwordHash)); }
 private void available(User u,String issuer,String subject) {
  if(u==null || u.passwordHash==null) throw new AuthProblem(409,"GOOGLE_LINK_UNAVAILABLE");
  if(!"ACTIVE".equals(u.status)) throw new AuthProblem(403,"GOOGLE_ACCOUNT_BLOCKED");
  if(hasGoogle(u) || AuthIdentity.count("issuer = ?1 and subject = ?2",issuer,subject)>0)
   throw new AuthProblem(409,"GOOGLE_LINK_UNAVAILABLE");
 }
 public GoogleAttempts.LinkTarget target(String email,String issuer,String subject,String sessionToken) {
  User owner=User.find("lower(email) = ?1",AuthService.email(email)).firstResult();
  User target=sessionToken==null?owner:sessions.require(sessionToken);
  if(owner!=null && !owner.id.equals(target.id)) throw new AuthProblem(409,"GOOGLE_LINK_UNAVAILABLE");
  available(target,issuer,subject);
  return new GoogleAttempts.LinkTarget(target.id,revision(target));
 }
 @Transactional public String link(String flow,GoogleAttempts.Registration r,String password,String currentSession) {
  lockIdentity(r.issuer(),r.subject());
  User u=User.findById(r.target().userId(),jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  available(u,r.issuer(),r.subject());
  if(!revision(u).equals(r.target().passwordRevision())) throw new AuthProblem(400,"GOOGLE_FLOW_INVALID");
  if(r.attempt().linking() && (!r.attempt().sessionToken().equals(currentSession) || !sessions.require(currentSession).id.equals(u.id)))
   throw new AuthProblem(403,"GOOGLE_FLOW_INVALID");
  if(User.count("lower(email) = ?1 and id <> ?2",AuthService.email(r.email()),u.id)>0)
   throw new AuthProblem(409,"GOOGLE_LINK_UNAVAILABLE");
  if(!passwords.verify(password,u.passwordHash)) throw new AuthProblem(401,"INVALID_CREDENTIALS");
  attempts.consumeRegistration(flow,r.confirmation(),true);
  attach(u,r.issuer(),r.subject());
  if(r.attempt().linking()) sessions.revoke(currentSession);
  return sessions.create(u);
 }
}
