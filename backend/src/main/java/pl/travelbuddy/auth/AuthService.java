package pl.travelbuddy.auth;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.time.*;
import java.util.*;
@ApplicationScoped
public class AuthService {
 @Inject Passwords passwords;
 @Inject SessionService sessions;
 @Inject ResetMailer mailer;
 static String email(String email) { return email.toLowerCase(Locale.ROOT); }
 static void confirmation(String password, String confirmation) {
  if (!password.equals(confirmation)) throw new AuthProblem(400,"PASSWORD_MISMATCH");
 }
 @Transactional public String register(AuthDtos.Register r) {
  confirmation(r.password(),r.passwordConfirmation());
  User u=new User(); u.id=UUID.randomUUID(); u.email=email(r.email()); u.passwordHash=passwords.hash(r.password());
  u.role="USER"; u.status="ACTIVE"; u.uiLocale=r.locale(); u.termsVersion="local-draft-1";
  u.createdAt=Instant.now(); u.updatedAt=u.createdAt; u.termsAcceptedAt=u.createdAt;
  if (User.count("lower(email) = ?1",u.email)>0) throw new AuthProblem(409,"EMAIL_UNAVAILABLE");
  try { u.persistAndFlush(); }
  catch (org.hibernate.exception.ConstraintViolationException e) { throw new AuthProblem(409,"EMAIL_UNAVAILABLE"); }
  return sessions.create(u);
 }
 @Transactional public String login(AuthDtos.Login r) {
  User u=User.find("lower(email) = ?1",email(r.email())).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
  boolean valid=passwords.verify(r.password(),u==null?null:u.passwordHash);
  if (!valid || !"ACTIVE".equals(u.status)) throw new AuthProblem(401,"INVALID_CREDENTIALS");
  return sessions.create(u);
 }
 @Transactional public void requestReset(String email) {
  User u=User.find("lower(email) = ?1",email(email)).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
  if (u==null || u.passwordHash==null || !"ACTIVE".equals(u.status)) return;
  String raw=Tokens.random();
  AuthToken t=new AuthToken(); t.id=UUID.randomUUID(); t.user=u; t.purpose="PASSWORD_RESET";
  t.tokenHash=Tokens.hash(raw); t.createdAt=Instant.now(); t.expiresAt=t.createdAt.plus(Duration.ofMinutes(30)); t.persistAndFlush();
  try { mailer.send(u,raw); }
  catch (RuntimeException e) {
   // Never log SMTP payload or token. Keep the HTTP answer neutral even on delivery failure.
   t.delete();
   org.jboss.logging.Logger.getLogger(AuthService.class).warn("Password reset delivery failed");
  }
 }
 @Transactional public void confirmReset(AuthDtos.ResetConfirm r) {
  confirmation(r.password(),r.passwordConfirmation());
  AuthToken t=AuthToken.find("tokenHash = ?1 and purpose = ?2",Tokens.hash(r.token()),"PASSWORD_RESET").firstResult();
  if (t==null) throw new AuthProblem(400,"RESET_INVALID");
  // Always lock user first: two tokens for one user, reset and login share lock order.
  User u=t.user;
  User.getEntityManager().refresh(u,LockModeType.PESSIMISTIC_WRITE);
  AuthToken.getEntityManager().refresh(t,LockModeType.PESSIMISTIC_WRITE);
  if (t.consumedAt!=null || !t.expiresAt.isAfter(Instant.now()) || !"ACTIVE".equals(u.status)) throw new AuthProblem(400,"RESET_INVALID");
  u.passwordHash=passwords.hash(r.password()); u.updatedAt=Instant.now();
  t.consumedAt=u.updatedAt;
  Session.delete("user",u);
  AuthToken.update("consumedAt = ?1 where user = ?2 and consumedAt is null",u.updatedAt,u);
 }
}
