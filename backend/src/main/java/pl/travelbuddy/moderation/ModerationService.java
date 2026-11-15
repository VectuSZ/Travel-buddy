package pl.travelbuddy.moderation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.persistence.LockModeType;
import com.fasterxml.jackson.databind.JsonNode;
import pl.travelbuddy.*;
import pl.travelbuddy.auth.*;
import pl.travelbuddy.profiles.*;
import java.util.*;
import java.time.*;
@ApplicationScoped
public class ModerationService {
 @Inject ReportChallenge challenge;
 @Inject jakarta.enterprise.event.Event<ModerationMail.Notice> notifications;
 @Inject jakarta.validation.Validator validator;
 public record Email(@jakarta.validation.constraints.Email String value){}
 public record View(UUID id,UUID targetTripId,UUID targetUserId,String targetUrl,String targetLabel,String targetContent,String targetStatus,String category,String description,String status,String resolutionReason,Instant createdAt){}
 public void admin(User user){User fresh=User.findById(user.id);if(fresh==null||!"ACTIVE".equals(fresh.status)||!"ADMIN".equals(fresh.role))throw new AuthProblem(403,"ADMIN_REQUIRED");}
 void fields(JsonNode n,Set<String> allowed){if(n==null||!n.isObject())bad();n.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))bad();});}
 void bad(){throw new AuthProblem(400,"INVALID_REPORT");}
 String text(JsonNode n,String key,int max,boolean required){var v=n.get(key);if(v==null||v.isNull()){if(required)bad();return null;}if(!v.isTextual())bad();String s=v.textValue().strip();if(s.length()>max||s.contains("<")||s.contains(">")||required&&s.isEmpty())bad();return s.isEmpty()?null:s;}
 UUID uuid(JsonNode n,String key){String s=text(n,key,36,false);try{return s==null?null:UUID.fromString(s);}catch(IllegalArgumentException e){bad();return null;}}
 @Transactional public UUID report(User user,JsonNode n){
  fields(n,Set.of("targetTripId","targetUserId","targetUrl","category","description","goodFaith","reporterName","reporterEmail","token"));
  UUID trip=uuid(n,"targetTripId"),target=uuid(n,"targetUserId");
  if((trip==null)==(target==null))bad();
  if(trip!=null){Trip t=Trip.findById(trip);if(t==null||"DRAFT".equals(t.status)||!"VISIBLE".equals(t.moderationStatus))throw new AuthProblem(404,"TARGET_NOT_FOUND");User owner=User.findById(t.organizerId);if(owner==null||!"ACTIVE".equals(owner.status))throw new AuthProblem(404,"TARGET_NOT_FOUND");}
  if(target!=null){User u=User.findById(target);if(u==null||!"ACTIVE".equals(u.status)||TravelerProfile.findById(target)==null)throw new AuthProblem(404,"TARGET_NOT_FOUND");}
  String url=text(n,"targetUrl",500,true);
  // Store only internal routes to the asserted target, never arbitrary URLs or query secrets.
  String targetId=(trip!=null?trip:target).toString();
  if(!url.matches(trip!=null?"^/(?:en/trips|trips|pl/wyprawy)/"+targetId+"(?:/[a-z0-9-]+)?$":"^/(?:en/)?profiles/"+targetId+"$"))bad();
  String category=text(n,"category",32,true);
  if(!Set.of("SPAM","HARASSMENT","FRAUD","UNSAFE","OTHER").contains(category))bad();
  String description=text(n,"description",4000,true);
  if(!n.path("goodFaith").isBoolean()||!n.path("goodFaith").booleanValue())bad();
  String name=user==null?text(n,"reporterName",120,true):null;
  String email=user==null?text(n,"reporterEmail",254,true):null;
  if(email!=null&&!validator.validate(new Email(email)).isEmpty())bad();
  challenge.verify(text(n,"token",2048,true));
  Report r=new Report();r.id=UUID.randomUUID();r.reporterId=user==null?null:user.id;r.reporterName=name;r.reporterEmail=email;r.targetTripId=trip;r.targetUserId=target;r.targetUrl=url;r.category=category;r.description=description;r.goodFaith=true;r.status="OPEN";r.createdAt=r.updatedAt=Instant.now();r.persist();return r.id;
 }
 View view(Report r){
  String label="",content="",status="";
  if(r.targetTripId!=null){Trip t=Trip.findById(r.targetTripId);if(t!=null){label=t.title;content=t.itinerary;status=t.moderationStatus;}}
  else if(r.targetUserId!=null){TravelerProfile p=TravelerProfile.findById(r.targetUserId);User u=User.findById(r.targetUserId);if(p!=null){label=p.displayName;content=p.bio;}if(u!=null)status=u.status;}
  return new View(r.id,r.targetTripId,r.targetUserId,r.targetUrl,label,content,status,r.category,r.description,r.status,r.resolutionReason,r.createdAt);
 }
 @Transactional public List<View> list(User u,String status){admin(u);if(!Set.of("OPEN","RESOLVED","DISMISSED").contains(status))bad();return Report.<Report>find("status = ?1 order by createdAt,id",status).list().stream().map(this::view).toList();}
 void audit(User u,UUID report,UUID trip,UUID target,String action,String reason){ModerationAction a=new ModerationAction();a.id=UUID.randomUUID();a.adminId=u.id;a.reportId=report;a.targetTripId=trip;a.targetUserId=target;a.action=action;a.reason=reason;a.createdAt=Instant.now();a.persist();}
 @Transactional public View resolve(User u,UUID id,JsonNode n){
  admin(u);fields(n,Set.of("status","reason"));String status=text(n,"status",16,true),reason=text(n,"reason",4000,true);
  if(!Set.of("RESOLVED","DISMISSED").contains(status))bad();
  Report r=Report.findById(id,LockModeType.PESSIMISTIC_WRITE);if(r==null)throw new AuthProblem(404,"REPORT_NOT_FOUND");
  if(!"OPEN".equals(r.status))throw new AuthProblem(409,"REPORT_STATE");
  r.status=status;r.resolvedBy=u.id;r.resolutionReason=reason;r.resolvedAt=r.updatedAt=Instant.now();audit(u,id,r.targetTripId,r.targetUserId,status,reason);
  User reporter=r.reporterId==null?null:User.findById(r.reporterId);
  String email=reporter==null?r.reporterEmail:reporter.email;
  if(email!=null)notifications.fire(new ModerationMail.Notice(email,reporter!=null&&"en".equals(reporter.uiLocale),status,reason));
  return view(r);
 }
 @Transactional public void act(User u,UUID id,String action,JsonNode n){
  admin(u);fields(n,Set.of("reason"));String reason=text(n,"reason",4000,true);
  if(Set.of("hide","unhide").contains(action)){
   Trip t=Trip.findById(id,LockModeType.PESSIMISTIC_WRITE);if(t==null)throw new AuthProblem(404,"TARGET_NOT_FOUND");
   t.moderationStatus=action.equals("hide")?"HIDDEN":"VISIBLE";t.updatedAt=Instant.now();audit(u,null,id,null,action.toUpperCase(Locale.ROOT),reason);
   User owner=User.findById(t.organizerId);
   if(owner!=null)notifications.fire(new ModerationMail.Notice(owner.email,"en".equals(owner.uiLocale),action,reason));
  }else{
   if(id.equals(u.id))throw new AuthProblem(409,"SELF_MODERATION");
   User target=User.findById(id,LockModeType.PESSIMISTIC_WRITE);if(target==null)throw new AuthProblem(404,"TARGET_NOT_FOUND");
   if("DELETION_PENDING".equals(target.status))throw new AuthProblem(409,"ACCOUNT_STATE");
   target.status=action.equals("block")?"BLOCKED":"ACTIVE";target.updatedAt=Instant.now();
   if(action.equals("block"))Session.delete("user.id",id);
   audit(u,null,null,id,action.toUpperCase(Locale.ROOT),reason);
   notifications.fire(new ModerationMail.Notice(target.email,"en".equals(target.uiLocale),action,reason));
  }
 }
}
