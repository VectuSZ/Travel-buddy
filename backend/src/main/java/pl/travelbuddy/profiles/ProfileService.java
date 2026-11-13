package pl.travelbuddy.profiles;
import pl.travelbuddy.auth.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.time.*;
import java.util.*;
@ApplicationScoped
public class ProfileService {
 public static final Set<String> LANGUAGES=Set.of("pl","en","de","fr","es","it","pt","uk","cs","sk","nl","sv","no","da","fi","el","tr","ar","zh","ja","ko","hi");
 public static final Set<String> STYLES=Set.of("backpacking","comfort","trekking","city_break");
 @Inject Validator validator;
 public record EmailValue(@Email String value) {}
 static void invalid(String field) { throw new AuthProblem(400,"INVALID_"+field); }
 static void fields(JsonNode n,Set<String> allowed) {
  if(n==null || !n.isObject()) invalid("INPUT");
  n.fieldNames().forEachRemaining(k->{ if(!allowed.contains(k)) invalid("INPUT"); });
 }
 static String text(JsonNode n,String key,boolean required,int max,String code) {
  if(n==null || n.isNull()) { if(required) invalid(code); return null; }
  if(!n.isTextual()) invalid(code);
  String s=n.textValue().strip();
  if(s.isEmpty()) { if(required) invalid(code); return null; }
  if(s.length()>max || s.indexOf('<')>=0 || s.indexOf('>')>=0) invalid(code);
  return s;
 }
 static String[] codes(JsonNode n,Set<String> allowed,int max,String code) {
  if(n==null || !n.isArray() || n.size()==0 || n.size()>32) invalid(code);
  Set<String> result=new LinkedHashSet<>();
  for(JsonNode item:n) { if(!item.isTextual() || !allowed.contains(item.textValue())) invalid(code); result.add(item.textValue()); }
  if(result.size()>max) invalid(code);
  return result.toArray(String[]::new);
 }
 public ProfileDtos.Me me(User user) {
  TravelerProfile p=TravelerProfile.findById(user.id); ProfileContacts c=ProfileContacts.findById(user.id);
  boolean complete=p!=null, contact=c!=null && (c.contactEmail!=null || c.instagramHandle!=null || c.whatsappE164!=null);
  return new ProfileDtos.Me(user.id,user.email,ProfileDtos.own(p),ProfileDtos.contacts(c),complete,contact,complete&&contact);
 }
 void lock(User user) {
  User current=User.findById(user.id,LockModeType.PESSIMISTIC_WRITE);
  if(current==null || !"ACTIVE".equals(current.status)) throw new AuthProblem(401,"UNAUTHENTICATED");
 }
 @Transactional public ProfileDtos.Me patch(User user,JsonNode n) {
  lock(user); fields(n,Set.of("displayName","age","bio","languages","travelStyles","version"));
  TravelerProfile p=TravelerProfile.findById(user.id); boolean fresh=p==null;
  if(!n.has("version") || !n.get("version").isIntegralNumber() || !n.get("version").canConvertToInt()) invalid("VERSION");
  if(n.get("version").intValue()!=(fresh?0:p.version)) throw new AuthProblem(409,"PROFILE_VERSION_CONFLICT");
  if(fresh) { p=new TravelerProfile();p.userId=user.id;p.version=1;p.createdAt=Instant.now(); }
  if(fresh || n.has("displayName")) { p.displayName=text(n.get("displayName"),"displayName",true,60,"DISPLAY_NAME");if(p.displayName.length()<2) invalid("DISPLAY_NAME"); }
  if(fresh || n.has("age")) {
   JsonNode age=n.get("age");if(age==null || !age.isIntegralNumber() || !age.canConvertToInt() || age.intValue()<18 || age.intValue()>120) invalid("AGE");
   if(fresh || p.age!=age.intValue()) p.ageUpdatedAt=LocalDate.now(ZoneOffset.UTC);
   p.age=(short)age.intValue();
  }
  if(n.has("bio")) p.bio=text(n.get("bio"),"bio",false,1000,"BIO");
  if(fresh || n.has("languages")) p.languages=codes(n.get("languages"),LANGUAGES,10,"LANGUAGES");
  if(fresh || n.has("travelStyles")) p.travelStyles=codes(n.get("travelStyles"),STYLES,4,"TRAVEL_STYLES");
  p.updatedAt=Instant.now();
  if(fresh) p.persist();
  try { TravelerProfile.getEntityManager().flush(); } catch(jakarta.persistence.OptimisticLockException e) { throw new AuthProblem(409,"PROFILE_VERSION_CONFLICT"); }
  return me(user);
 }
 @Transactional public ProfileDtos.Me contacts(User user,JsonNode n) {
  lock(user); fields(n,Set.of("contactEmail","instagramHandle","whatsappE164"));
  String email=text(n.get("contactEmail"),"contactEmail",false,254,"CONTACT_EMAIL");
  if(email!=null && !validator.validate(new EmailValue(email)).isEmpty()) invalid("CONTACT_EMAIL");
  String instagram=text(n.get("instagramHandle"),"instagramHandle",false,31,"INSTAGRAM");
  if(instagram!=null && instagram.startsWith("@")) instagram=instagram.substring(1);
  if(instagram!=null && !instagram.matches("[A-Za-z0-9._]{1,30}")) invalid("INSTAGRAM");
  String whatsapp=text(n.get("whatsappE164"),"whatsappE164",false,16,"WHATSAPP");
  if(whatsapp!=null && !whatsapp.matches("[+][1-9][0-9]{1,14}")) invalid("WHATSAPP");
  ProfileContacts c=ProfileContacts.findById(user.id);
  boolean fresh=c==null;
  if(fresh) { c=new ProfileContacts();c.userId=user.id; }
  c.contactEmail=email;c.instagramHandle=instagram;c.whatsappE164=whatsapp;c.updatedAt=Instant.now();
  if(fresh) c.persist();
  return me(user);
 }
 public ProfileDtos.PublicProfile publicProfile(UUID id) {
  User u=User.findById(id);TravelerProfile p=TravelerProfile.findById(id);
  if(u==null || !"ACTIVE".equals(u.status) || p==null) throw new AuthProblem(404,"PROFILE_NOT_FOUND");
  return ProfileDtos.publicDto(p);
 }
}
