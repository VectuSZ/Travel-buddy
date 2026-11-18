package pl.travelbuddy.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pl.travelbuddy.*;
import java.util.*;

@ApplicationScoped
public class DataRetention {
 @Inject EntityManager em;
 @Inject DataClock clock;
 @ConfigProperty(name="privacy.batch-size",defaultValue="50") int batch;
 @ConfigProperty(name="privacy.trip-months",defaultValue="12") int tripMonths;
 @ConfigProperty(name="privacy.moderation-months",defaultValue="12") int moderationMonths;
 @Transactional public List<UUID> jobs() {
  return em.createNativeQuery("SELECT user_id FROM {h-schema}deletion_jobs WHERE next_attempt_at <= :now ORDER BY next_attempt_at",UUID.class).setParameter("now",clock.now()).setMaxResults(batch).getResultList();
 }
 @Transactional public void failed(UUID id) {
  em.createNativeQuery("UPDATE {h-schema}deletion_jobs SET next_attempt_at = :next WHERE user_id = :id")
    .setParameter("next",clock.now().plusSeconds(3600)).setParameter("id",id).executeUpdate();
 }
 @Transactional public void clean() {
  for(Trip t:Trip.<Trip>find("status = 'OPEN' and dateTo < ?1 order by dateTo",clock.now().atZone(java.time.ZoneId.of("Europe/Warsaw")).toLocalDate()).withLock(LockModeType.PESSIMISTIC_WRITE).page(0,batch).list())
   TripService.close(t,clock.now());
  for(Trip t:Trip.<Trip>find("status = 'CLOSED' and dateTo < ?1 order by dateTo",clock.now().atZone(java.time.ZoneId.of("Europe/Warsaw")).toLocalDate().minusMonths(tripMonths)).withLock(LockModeType.PESSIMISTIC_WRITE).page(0,batch).list()) {
   TripApplication.delete("tripId",t.id); t.delete();
  }
  var cutoff=clock.now().atZone(java.time.ZoneId.of("Europe/Warsaw")).minusMonths(moderationMonths).toInstant();
  em.createNativeQuery("DELETE FROM {h-schema}moderation_actions WHERE id IN (SELECT id FROM {h-schema}moderation_actions WHERE created_at < :cutoff ORDER BY created_at LIMIT :batch)").setParameter("cutoff",cutoff).setParameter("batch",batch).executeUpdate();
  em.createNativeQuery("DELETE FROM {h-schema}reports WHERE id IN (SELECT id FROM {h-schema}reports WHERE status <> 'OPEN' AND resolved_at < :cutoff ORDER BY resolved_at LIMIT :batch)").setParameter("cutoff",cutoff).setParameter("batch",batch).executeUpdate();
 }
}
