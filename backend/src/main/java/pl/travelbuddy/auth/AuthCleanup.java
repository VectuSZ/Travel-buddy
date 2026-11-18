package pl.travelbuddy.auth;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
@ApplicationScoped
public class AuthCleanup {
 @jakarta.inject.Inject DataClock clock;
 @org.eclipse.microprofile.config.inject.ConfigProperty(name="privacy.batch-size",defaultValue="50") int batch;
 @Scheduled(every="1h", concurrentExecution=Scheduled.ConcurrentExecution.SKIP)
 @Transactional public void clean() {
  Instant now=clock.now();
  for(Session s:Session.<Session>find("expiresAt <= ?1",now).page(0,batch).list()) s.delete();
  for(AuthToken t:AuthToken.<AuthToken>find("expiresAt <= ?1 or consumedAt is not null",now).page(0,batch).list()) t.delete();
 }
}
