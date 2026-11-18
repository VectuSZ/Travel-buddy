package pl.travelbuddy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import pl.travelbuddy.auth.*;
import pl.travelbuddy.profiles.*;
import java.util.*;
import java.time.*;
@ApplicationScoped
public class ApplicationService {
 @Inject ProfileService profiles;
 public record View(UUID id,UUID tripId,String tripTitle,UUID organizerId,UUID applicantId,String displayName,String message,String status){}
 void fail(int code,String message){throw new AuthProblem(code,message);}
 public Trip locked(UUID id){Trip t=Trip.findById(id,LockModeType.PESSIMISTIC_WRITE);if(t==null)fail(404,"TRIP_NOT_FOUND");return t;}
 void active(UUID id){User u=User.findById(id);if(u==null||!"ACTIVE".equals(u.status))fail(404,"TRIP_NOT_FOUND");}
 void visible(Trip t){if(!"VISIBLE".equals(t.moderationStatus)||"DRAFT".equals(t.status))fail(404,"TRIP_NOT_FOUND");active(t.organizerId);}
 void open(Trip t){visible(t);if(!"OPEN".equals(t.status))fail(409,"TRIP_CLOSED");}
 long accepted(UUID id){return TripApplication.count("tripId = ?1 and status = 'ACCEPTED'",id);}
 void space(Trip t){if(accepted(t.id)+1>=t.targetPeople)fail(409,"TRIP_FULL");}
 View view(TripApplication a){Trip t=Trip.findById(a.tripId);TravelerProfile p=TravelerProfile.findById(a.applicantId);return new View(a.id,a.tripId,t.title,t.organizerId,a.applicantId,p==null?"":p.displayName,a.message,a.status);}
 @Transactional public View apply(User u,UUID id,com.fasterxml.jackson.databind.JsonNode input){
  User applicant=User.findById(u.id,LockModeType.PESSIMISTIC_WRITE);
  if(applicant==null)fail(401,"UNAUTHENTICATED");
  User.getEntityManager().refresh(applicant);
  Trip t=locked(id);active(u.id);open(t);
  if(t.organizerId.equals(u.id))fail(409,"OWN_TRIP");
  if(input==null||!input.isObject()||input.size()!=1||!input.has("message")||!input.get("message").isTextual())fail(400,"INVALID_MESSAGE");
  String message=input.get("message").textValue().strip();if(message.isEmpty()||message.length()>2000||message.contains("<")||message.contains(">"))fail(400,"INVALID_MESSAGE");
  if(!profiles.me(u).readyToParticipate())fail(400,"PROFILE_CONTACT_REQUIRED");
  if(TripApplication.count("tripId = ?1 and applicantId = ?2",id,u.id)>0)fail(409,"ALREADY_APPLIED");
  space(t);
  if(TripApplication.count("applicantId = ?1 and createdAt >= ?2",u.id,LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC))>=20)fail(429,"APPLICATION_LIMIT");
  TripApplication a=new TripApplication();a.id=UUID.randomUUID();a.tripId=id;a.applicantId=u.id;a.message=message;a.status="PENDING";a.createdAt=a.updatedAt=Instant.now();a.persist();return view(a);
 }
 @Transactional public List<View> mine(User u){return TripApplication.<TripApplication>list("applicantId = ?1 order by createdAt desc",u.id).stream().map(this::view).toList();}
 @Transactional public List<View> incoming(User u,UUID id){Trip t=Trip.findById(id);if(t==null||!u.id.equals(t.organizerId))fail(404,"TRIP_NOT_FOUND");return TripApplication.<TripApplication>list("tripId = ?1 order by createdAt",id).stream().map(this::view).toList();}
 @Transactional public View decide(User u,UUID id,String action){
  TripApplication found=TripApplication.findById(id);if(found==null)fail(404,"APPLICATION_NOT_FOUND");
  for(UUID uid:new java.util.TreeSet<>(java.util.List.of(u.id,found.applicantId))) {
   User current=User.findById(uid,LockModeType.PESSIMISTIC_WRITE);
   if(current==null)fail(404,"APPLICATION_NOT_FOUND");
   User.getEntityManager().refresh(current);
   if(!"ACTIVE".equals(current.status))fail(404,"APPLICATION_NOT_FOUND");
  }
  Trip t=locked(found.tripId);
  TripApplication.getEntityManager().refresh(found);active(u.id);
  if(action.equals("withdraw")){
   if(!found.applicantId.equals(u.id))fail(404,"APPLICATION_NOT_FOUND");
   if(!Set.of("PENDING","ACCEPTED").contains(found.status))fail(409,"APPLICATION_STATE");
   found.status="WITHDRAWN";
  }else{
   if(!t.organizerId.equals(u.id))fail(404,"APPLICATION_NOT_FOUND");
   open(t);active(found.applicantId);
   if(!"PENDING".equals(found.status))fail(409,"APPLICATION_STATE");
   if(action.equals("accept")){space(t);found.status="ACCEPTED";if(t.firstAcceptedAt==null)t.firstAcceptedAt=Instant.now();}
   else found.status="REJECTED";
   found.decidedAt=Instant.now();
  }
  found.updatedAt=Instant.now();return view(found);
 }
 @Transactional public List<ProfileDtos.PublicProfile> participants(UUID id){
  Trip t=Trip.findById(id);if(t==null)fail(404,"TRIP_NOT_FOUND");visible(t);
  List<UUID> ids=new ArrayList<>();ids.add(t.organizerId);
  for(TripApplication a:TripApplication.<TripApplication>list("tripId = ?1 and status = 'ACCEPTED'",id))ids.add(a.applicantId);
  List<ProfileDtos.PublicProfile> result=new ArrayList<>();
  for(UUID uid:ids){User u=User.findById(uid);TravelerProfile p=TravelerProfile.findById(uid);if(u!=null&&"ACTIVE".equals(u.status)&&p!=null)result.add(ProfileDtos.publicDto(p));}
  return result;
 }
 @Transactional public ProfileDtos.Contacts contacts(User u,UUID id,UUID target){
  Trip t=Trip.findById(id);if(t==null)fail(404,"TRIP_NOT_FOUND");visible(t);active(u.id);active(target);
  UUID applicant;
  if(u.id.equals(t.organizerId)&&!target.equals(u.id))applicant=target;
  else if(target.equals(t.organizerId)&&!target.equals(u.id))applicant=u.id;
  else {fail(403,"CONTACT_FORBIDDEN");return null;}
  if(TripApplication.count("tripId = ?1 and applicantId = ?2 and status = 'ACCEPTED'",id,applicant)!=1)fail(403,"CONTACT_FORBIDDEN");
  return ProfileDtos.contacts(ProfileContacts.findById(target));
 }
}
