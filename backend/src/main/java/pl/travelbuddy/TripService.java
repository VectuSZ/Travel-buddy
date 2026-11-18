package pl.travelbuddy;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import pl.travelbuddy.auth.*;
import pl.travelbuddy.profiles.*;
import java.time.*;
import java.util.*;
@ApplicationScoped
public class TripService {
 @Inject ProfileService profiles;
 @org.eclipse.microprofile.config.inject.ConfigProperty(name="trips.publish-per-day",defaultValue="3") int perDay;
 @org.eclipse.microprofile.config.inject.ConfigProperty(name="trips.max-open",defaultValue="5") int maxOpen;
 static void bad(String field) { throw new AuthProblem(400,"INVALID_"+field); }
 void fields(JsonNode n, Set<String> keys) {
  if(n==null || !n.isObject()) bad("INPUT");
  n.fieldNames().forEachRemaining(k->{if(!keys.contains(k))bad("INPUT");});
 }
 void lock(User u) {
  User current=User.findById(u.id,LockModeType.PESSIMISTIC_WRITE);
  if(current!=null) User.getEntityManager().refresh(current);
  if(current==null || !"ACTIVE".equals(current.status)) throw new AuthProblem(401,"UNAUTHENTICATED");
 }
 Trip own(User u, UUID id) {
  Trip t=Trip.findById(id,LockModeType.PESSIMISTIC_WRITE);
  if(t==null || !u.id.equals(t.organizerId)) throw new AuthProblem(404,"TRIP_NOT_FOUND");
  return t;
 }
 public TripView view(Trip t) {
  TravelerProfile p=TravelerProfile.findById(t.organizerId);
  return TripView.of(t,p==null?null:profiles.publicProfile(t.organizerId));
 }
 public List<TripView> mine(User u) {return Trip.<Trip>list("organizerId = ?1 order by createdAt desc, id",u.id).stream().map(this::view).toList();}
 @Transactional public TripView mine(User u,UUID id) {return view(own(u,id));}
 void version(Trip t,JsonNode n) {
  if(!n.has("version") || !n.get("version").isIntegralNumber() || !n.get("version").canConvertToInt())bad("VERSION");
  if(t.version!=n.get("version").intValue())throw new AuthProblem(409,"TRIP_VERSION_CONFLICT");
 }
 String text(JsonNode n,int max,String key) {
  if(n==null || n.isNull())return null;
  if(!n.isTextual())bad(key);
  String s=n.textValue().strip();
  if(s.length()>max || s.contains("<") || s.contains(">"))bad(key);
  return s.isEmpty()?null:s;
 }
 void content(Trip t,JsonNode n) {
  fields(n,Set.of("version","title","itinerary","lookingFor","contentLanguage","dateMode","dateFrom","dateTo","durationDays","targetPeople","budgetMin","budgetMax","styles","locations"));
  for(String key:List.of("title","itinerary","lookingFor","contentLanguage","dateMode")) if(n.has(key)) {
   int max=switch(key){case "title"->120;case "itinerary"->10000;case "lookingFor"->2000;case "contentLanguage"->35;default->16;};
   String value=text(n.get(key),max,key);
   switch(key){case "title"->t.title=value;case "itinerary"->t.itinerary=value;case "lookingFor"->t.lookingFor=value;case "contentLanguage"->t.contentLanguage=value;case "dateMode"->t.dateMode=value;}
  }
  for(String key:List.of("dateFrom","dateTo","durationDays","targetPeople","budgetMin","budgetMax")) if(n.has(key)) {
   JsonNode v=n.get(key);Object value=null;
   try {
    if(!v.isNull()) {
     if(key.startsWith("date")) {if(!v.isTextual())bad(key);value=LocalDate.parse(v.textValue());}
     else if(key.startsWith("budget")) {if(!v.isNumber())bad(key);var b=v.decimalValue();if(b.scale()>2 || b.signum()<0 || b.compareTo(new java.math.BigDecimal("99999999.99"))>0)bad(key);value=b;}
     else {if(!v.isIntegralNumber() || !v.canConvertToInt() || v.intValue()<1 || v.intValue()>32767)bad(key);value=(short)v.intValue();}
    }
    switch(key){case "dateFrom"->t.dateFrom=(LocalDate)value;case "dateTo"->t.dateTo=(LocalDate)value;case "durationDays"->t.durationDays=(Short)value;case "targetPeople"->t.targetPeople=(Short)value;case "budgetMin"->t.budgetMin=(java.math.BigDecimal)value;case "budgetMax"->t.budgetMax=(java.math.BigDecimal)value;}
   }catch(java.time.format.DateTimeParseException e){bad(key);}
  }
  if(t.dateMode!=null && !Set.of("EXACT","FLEXIBLE").contains(t.dateMode))bad("DATE_MODE");
  if(t.contentLanguage!=null && !ProfileService.LANGUAGES.contains(t.contentLanguage))bad("CONTENT_LANGUAGE");
  if(t.targetPeople!=null && (t.targetPeople<2 || t.targetPeople>50))bad("TARGET_PEOPLE");
  if(t.dateFrom!=null && t.dateTo!=null && (t.dateFrom.isAfter(t.dateTo) || ("FLEXIBLE".equals(t.dateMode) && t.durationDays!=null && t.durationDays>java.time.temporal.ChronoUnit.DAYS.between(t.dateFrom,t.dateTo)+1)))bad("DATES");
  if(t.budgetMin!=null && t.budgetMax!=null && t.budgetMin.compareTo(t.budgetMax)>0)bad("BUDGET");
  if(n.has("styles")) {
   if(!n.get("styles").isArray() || n.get("styles").size()>4)bad("STYLES");
   List<String> styles=new ArrayList<>();for(JsonNode v:n.get("styles")){if(!v.isTextual() || !ProfileService.STYLES.contains(v.textValue()))bad("STYLES");styles.add(v.textValue());}
   t.styles=styles.stream().distinct().toArray(String[]::new);
  }
  if(n.has("locations")) {
   JsonNode list=n.get("locations");if(!list.isArray() || list.size()>50)bad("LOCATIONS");
   TripLocation.delete("tripId",t.id);
   short position=0;
   for(JsonNode item:list) {
    fields(item,Set.of("countryCode","city"));String code=text(item.get("countryCode"),2,"COUNTRY");
    if(code==null || !Set.of(Locale.getISOCountries()).contains(code))bad("COUNTRY");
    TripLocation l=new TripLocation();l.id=UUID.randomUUID();l.tripId=t.id;l.position=position++;l.countryCode=code;l.city=text(item.get("city"),120,"CITY");l.persist();
   }
  }
 }
 void complete(Trip t) {
  if(t.title==null || t.itinerary==null || t.lookingFor==null || t.contentLanguage==null || t.dateMode==null || t.dateFrom==null || t.dateTo==null || t.targetPeople==null || t.budgetMin==null || t.budgetMax==null || t.styles.length==0 || ("FLEXIBLE".equals(t.dateMode)&&t.durationDays==null) || TripLocation.count("tripId",t.id)==0)bad("TRIP_INCOMPLETE");
 }
 @Transactional public TripView create(User u,JsonNode n) {
  lock(u);Trip t=new Trip();t.id=UUID.randomUUID();t.organizerId=u.id;t.status="DRAFT";t.moderationStatus="VISIBLE";t.currency="PLN";t.createdAt=t.updatedAt=Instant.now();t.persist();
  content(t,n);Trip.getEntityManager().flush();return view(t);
 }
 @Transactional public TripView patch(User u,UUID id,JsonNode n) {
  lock(u);Trip t=own(u,id);version(t,n);if("CLOSED".equals(t.status))throw new AuthProblem(409,"TRIP_CLOSED");
  if(t.firstAcceptedAt!=null){
   var before=view(t);var oldTarget=t.targetPeople;
   content(t,n);
   var after=view(t);
   if(!java.util.Objects.equals(before.dateMode(),after.dateMode())||!java.util.Objects.equals(before.dateFrom(),after.dateFrom())||!java.util.Objects.equals(before.dateTo(),after.dateTo())||!java.util.Objects.equals(before.durationDays(),after.durationDays())||(after.budgetMin()==null||before.budgetMin().compareTo(after.budgetMin())!=0)||(after.budgetMax()==null||before.budgetMax().compareTo(after.budgetMax())!=0)||!before.locations().stream().map(TripView.Location::countryCode).toList().equals(after.locations().stream().map(TripView.Location::countryCode).toList())||t.targetPeople==null||t.targetPeople<oldTarget)throw new AuthProblem(409,"TRIP_TERMS_LOCKED");
  }else content(t,n);
  if(t.targetPeople!=null && t.targetPeople<1+TripApplication.count("tripId = ?1 and status = 'ACCEPTED'",id))throw new AuthProblem(409,"TRIP_FULL");
  if("OPEN".equals(t.status))complete(t);t.updatedAt=Instant.now();Trip.getEntityManager().flush();return view(t);
 }
 @Transactional public TripView transition(User u,UUID id,JsonNode n,boolean publish) {
  lock(u);fields(n,Set.of("version"));Trip t=own(u,id);version(t,n);
  if(!(publish?"DRAFT":"OPEN").equals(t.status))throw new AuthProblem(409,"TRIP_STATE_CONFLICT");
  if(publish) {
   var me=profiles.me(u);
   if(!me.profileComplete())bad("PROFILE_REQUIRED");if(!me.hasContact())bad("CONTACT_REQUIRED");complete(t);
   if(Trip.count("organizerId = ?1 and publishedAt >= ?2",u.id,LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC))>=perDay || Trip.count("organizerId = ?1 and status = 'OPEN'",u.id)>=maxOpen)throw new AuthProblem(429,"TRIP_PUBLICATION_LIMIT");
   t.status="OPEN";t.publishedAt=Instant.now();
  } else {close(t,Instant.now());}
  t.updatedAt=Instant.now();Trip.getEntityManager().flush();return view(t);
 }
 public static void close(Trip t,Instant now) {
  t.status="CLOSED";t.closedAt=now;t.updatedAt=now;
  TripApplication.update("status = 'REJECTED', decidedAt = ?1, updatedAt = ?1 where tripId = ?2 and status = 'PENDING'",now,t.id);
 }
}
