package pl.travelbuddy.auth;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;
@ApplicationScoped
public class DeletionWorker {
 @Inject AccountDeletion deletion;
 @Inject DataRetention retention;
 @Scheduled(every="${privacy.cleanup-interval:1h}", concurrentExecution=Scheduled.ConcurrentExecution.SKIP)
 public void run() {
  for(var id:retention.jobs()) {
   try { deletion.erase(id); }
   catch(RuntimeException e) { retention.failed(id); org.jboss.logging.Logger.getLogger(DeletionWorker.class).warn("Account deletion failed; durable job retained for retry"); }
  }
  retention.clean();
 }
}
