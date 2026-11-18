package pl.travelbuddy;
@jakarta.enterprise.context.ApplicationScoped
@jakarta.enterprise.inject.Alternative
public class TestDataClock extends pl.travelbuddy.auth.DataClock {
 public static volatile java.time.Instant value;
 @Override public java.time.Instant now() { return value==null?java.time.Instant.now():value; }
}
