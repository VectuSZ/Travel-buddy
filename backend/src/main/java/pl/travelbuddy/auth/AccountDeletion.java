package pl.travelbuddy.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AccountDeletion {
    @Inject SessionService sessions;
    @Inject EntityManager em;

    @Inject Passwords passwords;
    @Inject DataClock clock;
    @org.eclipse.microprofile.config.inject.ConfigProperty(name="privacy.reauthentication-seconds", defaultValue="300") long confirmationSeconds;

    Session session(String token) {
        User user=sessions.require(token);
        em.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (!"ACTIVE".equals(user.status)) throw new AuthProblem(401,"UNAUTHENTICATED");
        Session session=Session.find("tokenHash",Tokens.hash(token)).firstResult();
        if(session==null || !session.expiresAt.isAfter(clock.now())) throw new AuthProblem(401,"UNAUTHENTICATED");
        return session;
    }
    @Transactional public void confirmPassword(String token,String password) {
        Session s=session(token);
        if(!passwords.verify(password,s.user.passwordHash)) throw new AuthProblem(401,"INVALID_CREDENTIALS");
        s.deletionConfirmedUntil=clock.now().plusSeconds(confirmationSeconds);
    }
    @Transactional public void confirmGoogle(String token,String issuer,String subject) {
        Session s=session(token);
        if(AuthIdentity.count("user.id = ?1 and issuer = ?2 and subject = ?3",s.user.id,issuer,subject)!=1)
            throw new AuthProblem(403,"GOOGLE_IDENTITY_MISMATCH");
        s.deletionConfirmedUntil=clock.now().plusSeconds(confirmationSeconds);
    }
    @Transactional public void request(String token) {
        Session s=session(token);
        if(s.deletionConfirmedUntil==null || !s.deletionConfirmedUntil.isAfter(clock.now()))
            throw new AuthProblem(403,"REAUTHENTICATION_REQUIRED");
        User user=s.user;
        user.status="DELETION_PENDING"; user.updatedAt=clock.now();
        em.createNativeQuery("INSERT INTO {h-schema}deletion_jobs(user_id,requested_at) VALUES (:id,:now)")
            .setParameter("id",user.id).setParameter("now",clock.now()).executeUpdate();
        pl.travelbuddy.TripApplication.update(
            "status = 'WITHDRAWN', updatedAt = ?1 where applicantId = ?2 and status in ('ACCEPTED','PENDING')",
            user.updatedAt, user.id);
        Session.delete("user",user);
    }
    @Transactional public void erase(java.util.UUID id) {
        User user=User.findById(id,LockModeType.PESSIMISTIC_WRITE);
        if(user==null) return;
        if(!"DELETION_PENDING".equals(user.status)) throw new IllegalStateException("Account is not pending deletion");
        // Free text cannot be reliably anonymized: remove it completely on related records.
        execute("UPDATE {h-schema}moderation_actions SET reason = '[removed]', admin_id = NULL, target_user_id = NULL, target_trip_id = NULL "
                + "WHERE admin_id = :id OR target_user_id = :id OR target_trip_id IN (SELECT id FROM {h-schema}trips WHERE organizer_id = :id) "
                + "OR report_id IN (SELECT id FROM {h-schema}reports WHERE reporter_id = :id OR lower(reporter_email) = (SELECT lower(email) FROM {h-schema}users WHERE id = :id) OR target_user_id = :id OR resolved_by = :id OR target_trip_id IN (SELECT id FROM {h-schema}trips WHERE organizer_id = :id))",id);
        execute("UPDATE {h-schema}reports SET reporter_id = NULL, reporter_name = NULL, reporter_email = NULL, target_user_id = NULL, target_trip_id = NULL, resolved_by = NULL, target_url = '[removed]', description = '[removed]', resolution_reason = '[removed]' "
                + "WHERE reporter_id = :id OR lower(reporter_email) = (SELECT lower(email) FROM {h-schema}users WHERE id = :id) OR target_user_id = :id OR resolved_by = :id OR target_trip_id IN (SELECT id FROM {h-schema}trips WHERE organizer_id = :id)",id);
        execute("DELETE FROM {h-schema}applications WHERE applicant_id = :id "
                + "OR trip_id IN (SELECT id FROM {h-schema}trips WHERE organizer_id = :id)", user.id);
        execute("DELETE FROM {h-schema}trips WHERE organizer_id = :id", user.id);
        execute("DELETE FROM {h-schema}users WHERE id = :id", user.id);
        em.clear();
    }

    private void execute(String sql, java.util.UUID id) {
        em.createNativeQuery(sql).setParameter("id", id).executeUpdate();
    }
}
