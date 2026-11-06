package pl.travelbuddy.auth;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.*;
import java.util.*;
@ApplicationScoped
public class RateLimiter {
 private record Window(Instant until, int count) {}
 private final Map<String,Window> windows = new HashMap<>();
 private final Clock clock;
 private final int capacity;
 @Inject public RateLimiter(@ConfigProperty(name="auth.rate.capacity",defaultValue="10000") int capacity) { this(Clock.systemUTC(), capacity); }
 RateLimiter(Clock clock, int capacity) { this.clock=clock; this.capacity=capacity; }
 public synchronized void check(String key, int limit, Duration duration) {
  Instant now=clock.instant();
  windows.entrySet().removeIf(e -> !e.getValue().until().isAfter(now));
  Window old=windows.get(key);
  if (old == null) {
   if (windows.size() >= capacity) throw new AuthProblem(429,"RATE_LIMITED");
   old=new Window(now.plus(duration),0);
  }
  if (old.count() >= limit) throw new AuthProblem(429,"RATE_LIMITED");
  windows.put(key,new Window(old.until(),old.count()+1));
 }
 synchronized void clear() { windows.clear(); }
}
