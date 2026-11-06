package pl.travelbuddy.auth;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
@ApplicationScoped
public class AuthCleanup {
 @Scheduled(every="1h", concurrentExecution=Scheduled.ConcurrentExecution.SKIP)
 @Transactional public void clean() {
  Instant now=Instant.now();
  Session.delete("expiresAt <= ?1",now);
  AuthToken.delete("expiresAt <= ?1 or consumedAt is not null",now);
 }
}
