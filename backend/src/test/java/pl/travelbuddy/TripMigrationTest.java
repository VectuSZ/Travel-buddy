package pl.travelbuddy;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class TripMigrationTest {
 @Test void freshAndUpgradePreserveDemo() throws Exception {
  for(boolean upgrade:new boolean[]{false,true}) {
   String name="tb_migration_"+UUID.randomUUID().toString().replace("-","");
   String base="jdbc:postgresql://127.0.0.1:5433/";
   String user=System.getenv("POSTGRES_USER"),password=System.getenv("POSTGRES_PASSWORD");
   try(var c=DriverManager.getConnection(base+System.getenv("POSTGRES_DB"),user,password);var s=c.createStatement()){s.execute("CREATE DATABASE "+name);}
   var config=Flyway.configure().dataSource(base+name,user,password).locations("db/migration","db/demo");
   if(upgrade) {
    config.target("2").load().migrate();
    try(var c=DriverManager.getConnection(base+name,user,password);var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM trips")){r.next();assertEquals(2,r.getInt(1));}
   }
   config.target("latest").load().migrate();
   config.load().validate();
   try(var c=DriverManager.getConnection(base+name,user,password);var s=c.createStatement();var r=s.executeQuery("""
    SELECT id,description,itinerary,start_date,date_from,end_date,date_to,organizer_id,status
    FROM trips WHERE id='11111111-1111-4111-8111-111111111111'
    """)) {
    assertTrue(r.next());assertEquals("11111111-1111-4111-8111-111111111111",r.getString(1));
    assertEquals("Spokojna wyprawa górska i wspólne odkrywanie szlaków.",r.getString(2));
    assertEquals(r.getString(2),r.getString(3));assertEquals(Date.valueOf("2027-06-12"),r.getDate(4));
    assertEquals(r.getDate(4),r.getDate(5));assertEquals(Date.valueOf("2027-06-14"),r.getDate(6));assertEquals(r.getDate(6),r.getDate(7));
    assertEquals("33333333-3333-4333-8333-333333333333",r.getString(8));assertEquals("OPEN",r.getString(9));
   }
   try(var c=DriverManager.getConnection(base+name,user,password);var s=c.createStatement();var r=s.executeQuery("SELECT password_hash, (SELECT count(*) FROM auth_identities WHERE user_id=users.id) FROM users WHERE id='33333333-3333-4333-8333-333333333333'")) {
    assertTrue(r.next());assertNull(r.getString(1));assertEquals(0,r.getInt(2));
   }
   System.out.println("Migration database retained: "+name);
  }
 }
}
