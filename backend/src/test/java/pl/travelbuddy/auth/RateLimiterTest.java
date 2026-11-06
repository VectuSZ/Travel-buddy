package pl.travelbuddy.auth;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class RateLimiterTest {
 @Test void boundedWindowsExpireWithoutSleeping() {
  class MutableClock extends Clock {
   Instant now=Instant.parse("2026-01-01T00:00:00Z");
   public ZoneId getZone(){return ZoneOffset.UTC;}
   public Clock withZone(ZoneId z){return this;}
   public Instant instant(){return now;}
  }
  var clock=new MutableClock(); var limiter=new RateLimiter(clock,2);
  limiter.check("a",1,Duration.ofMinutes(15));
  assertThrows(AuthProblem.class,()->limiter.check("a",1,Duration.ofMinutes(15)));
  limiter.check("b",1,Duration.ofMinutes(15));
  assertThrows(AuthProblem.class,()->limiter.check("c",1,Duration.ofMinutes(15)));
  clock.now=clock.now.plusSeconds(900);
  assertDoesNotThrow(()->limiter.check("a",1,Duration.ofMinutes(15)));
  assertDoesNotThrow(()->limiter.check("c",1,Duration.ofMinutes(15)));
 }
}
