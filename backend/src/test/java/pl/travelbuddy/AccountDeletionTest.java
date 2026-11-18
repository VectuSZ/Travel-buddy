package pl.travelbuddy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pl.travelbuddy.auth.*;
import pl.travelbuddy.profiles.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@io.quarkus.test.junit.TestProfile(AccountDeletionTest.Isolated.class)
class AccountDeletionTest extends ApplicationServiceTest {
 public static class Isolated implements io.quarkus.test.junit.QuarkusTestProfile {
  public java.util.Set<Class<?>> getEnabledAlternatives(){return java.util.Set.of(TestDataClock.class);}
  public java.util.Map<String,String> getConfigOverrides() { return java.util.Map.of("quarkus.flyway.schemas","privacy_test","quarkus.hibernate-orm.database.default-schema","privacy_test"); }
 }
 @org.junit.jupiter.api.BeforeEach void clockSetup(){TestDataClock.value=null;}
 @Inject AccountDeletion deletion;
 @Inject Passwords passwords;
 @Inject DataRetention retention;
 String confirm(User user) {
  String token=QuarkusTransaction.requiringNew().call(()-> {
   User current=User.findById(user.id);current.passwordHash=passwords.hash("deletion-test-password");
   User.getEntityManager().flush(); return sessions.create(current);
  });
  deletion.confirmPassword(token,"deletion-test-password");return token;
 }
 @Test void participantDeletionRevokesAllSessionsAndKeepsOtherTrip() {
  var application=apply(b);service.decide(a,application.id(),"accept");
  String token=confirm(b),second=confirm(b);
  deletion.request(token);
  assertThrows(AuthProblem.class,()->sessions.require(token));
  assertThrows(AuthProblem.class,()->sessions.require(second));
  assertThrows(AuthProblem.class,()->service.contacts(a,trip,b.id));
  assertThrows(AuthProblem.class,()->service.decide(a,application.id(),"accept"));
  deletion.erase(b.id);deletion.erase(b.id);
  QuarkusTransaction.requiringNew().run(()-> {
   assertNull(User.findById(b.id)); assertNull(TravelerProfile.findById(b.id));assertNull(ProfileContacts.findById(b.id));
   assertEquals(0,TripApplication.count("applicantId",b.id));assertNotNull(Trip.findById(trip));
   assertEquals(0,Session.count("user.id",b.id));assertEquals(0,AuthIdentity.count("user.id",b.id));assertEquals(0,AuthToken.count("user.id",b.id));
  });
 }
 @Test void organizerDeletionRemovesDependenciesAndDurableJob() {
  apply(b);apply(c);String token=confirm(a);deletion.request(token);
  assertTrue(retention.jobs().contains(a.id));
  assertThrows(AuthProblem.class,()->trips.patch(a,trip,json.createObjectNode().put("version",0).put("title","Changed")));
  deletion.erase(a.id);
  QuarkusTransaction.requiringNew().run(()-> {
   assertNull(User.findById(a.id));assertNull(Trip.findById(trip));
   assertEquals(0,TripLocation.count("tripId",trip));assertEquals(0,TripApplication.count("tripId",trip));
   assertNotNull(User.findById(b.id));assertNotNull(User.findById(c.id));
  });
  assertFalse(retention.jobs().contains(a.id));assertTrue(service.mine(b).isEmpty());
 }
 @Test void failedTransactionRetainsJobAndMinimizesModeration() {
  java.util.UUID report=java.util.UUID.randomUUID(),action=java.util.UUID.randomUUID();
  QuarkusTransaction.requiringNew().run(()-> {
   var em=User.getEntityManager();
   em.createNativeQuery("INSERT INTO {h-schema}reports(id,reporter_id,target_user_id,target_url,category,description,good_faith,status,created_at,updated_at,resolved_at,resolution_reason,reporter_email) VALUES (:r,:u,:u,'/private','OTHER','private text',true,'RESOLVED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'private reason','private@example.test')").setParameter("r",report).setParameter("u",b.id).executeUpdate();
   em.createNativeQuery("INSERT INTO {h-schema}moderation_actions(id,report_id,target_user_id,action,reason,created_at) VALUES (:a,:r,:u,'BLOCK','private reason',CURRENT_TIMESTAMP)").setParameter("a",action).setParameter("r",report).setParameter("u",b.id).executeUpdate();
  });
  try {
   deletion.request(confirm(b));
   assertThrows(IllegalStateException.class,()->QuarkusTransaction.requiringNew().run(()->{deletion.erase(b.id);throw new IllegalStateException("Simulated failure before commit");}));
   assertTrue(retention.jobs().contains(b.id));
   QuarkusTransaction.requiringNew().run(()->assertNotNull(User.findById(b.id)));
   deletion.erase(b.id);
   QuarkusTransaction.requiringNew().run(()-> {
    var r=(Object[])User.getEntityManager().createNativeQuery("SELECT reporter_id,reporter_email,description,resolution_reason,target_url FROM {h-schema}reports WHERE id=:id",Object[].class).setParameter("id",report).getSingleResult();
    assertNull(r[0]);assertNull(r[1]);assertEquals("[removed]",r[2]);assertEquals("[removed]",r[3]);assertEquals("[removed]",r[4]);
    assertEquals("[removed]",User.getEntityManager().createNativeQuery("SELECT reason FROM {h-schema}moderation_actions WHERE id=:id",String.class).setParameter("id",action).getSingleResult());
   });
   TestDataClock.value=Instant.now().plusSeconds(366L*86400);
   retention.clean();
   QuarkusTransaction.requiringNew().run(()->assertEquals(0L,User.getEntityManager().createNativeQuery("SELECT count(*) FROM {h-schema}reports WHERE id=:id",Long.class).setParameter("id",report).getSingleResult()));
  } finally { QuarkusTransaction.requiringNew().run(()-> {
   User.getEntityManager().createNativeQuery("DELETE FROM {h-schema}moderation_actions WHERE id=:id").setParameter("id",action).executeUpdate();
   User.getEntityManager().createNativeQuery("DELETE FROM {h-schema}reports WHERE id=:id").setParameter("id",report).executeUpdate();
  }); }
 }
 @Test void deletionRacesAcceptanceWithoutRestoringAccess() throws Exception {
  var application=apply(b);String token=confirm(b);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
   var latch=new java.util.concurrent.CountDownLatch(1);
   var removal=pool.submit(()->{latch.await();deletion.request(token);return true;});
   var decision=pool.submit(()->{latch.await();try{service.decide(a,application.id(),"accept");}catch(AuthProblem expected){}return true;});
   latch.countDown();removal.get(10,java.util.concurrent.TimeUnit.SECONDS);decision.get(10,java.util.concurrent.TimeUnit.SECONDS);
  }
  assertThrows(AuthProblem.class,()->service.contacts(a,trip,b.id));
  deletion.erase(b.id);
  QuarkusTransaction.requiringNew().run(()->assertEquals(0,TripApplication.count("applicantId",b.id)));
 }
 @Test void confirmationExpiresAndIsSessionBound() {
  String first=confirm(b);
  String second=QuarkusTransaction.requiringNew().call(()->sessions.create(User.findById(b.id)));
  assertThrows(AuthProblem.class,()->deletion.request(second));
  TestDataClock.value=Instant.now().plusSeconds(300);
  assertThrows(AuthProblem.class,()->deletion.request(first));
 }
 @Test void closureUsesWarsawDayAndRetentionBoundary() {
  apply(b);
  TestDataClock.value=Instant.parse("2027-01-04T22:59:59Z");
  retention.clean();
  QuarkusTransaction.requiringNew().run(()->assertEquals("OPEN",((Trip)Trip.findById(trip)).status));
  TestDataClock.value=Instant.parse("2027-01-04T23:00:00Z");
  retention.clean();
  assertEquals("REJECTED",service.mine(b).getFirst().status());
  TestDataClock.value=Instant.parse("2028-01-04T12:00:00Z");
  retention.clean();QuarkusTransaction.requiringNew().run(()->assertNotNull(Trip.findById(trip)));
  TestDataClock.value=Instant.parse("2028-01-05T12:00:00Z");
  retention.clean();QuarkusTransaction.requiringNew().run(()->assertNull(Trip.findById(trip)));
 }
}
