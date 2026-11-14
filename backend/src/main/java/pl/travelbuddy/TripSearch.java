package pl.travelbuddy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import pl.travelbuddy.auth.AuthProblem;
import pl.travelbuddy.profiles.ProfileService;
@ApplicationScoped
public class TripSearch {
 @Inject TripService service;
 public record Results(List<TripView> items,long total,int page,int size){}
 public Results search(String country,String from,String to,String min,String max,String style,String pageText,String sizeText){
  Map<String,Object> args=new HashMap<>();
  StringBuilder where=new StringBuilder(" FROM {h-schema}trips t JOIN {h-schema}users u ON u.id=t.organizer_id WHERE t.status='OPEN' AND t.moderation_status='VISIBLE' AND u.status='ACTIVE'");
  int page,size;
  try{
   page=pageText==null?1:Integer.parseInt(pageText);size=sizeText==null?20:Integer.parseInt(sizeText);
   if(page<1||page>1000000||size<1||size>50)throw new IllegalArgumentException();
   LocalDate a=from==null?null:LocalDate.parse(from),b=to==null?null:LocalDate.parse(to);
   BigDecimal low=min==null?null:new BigDecimal(min),high=max==null?null:new BigDecimal(max);
   if(a!=null&&b!=null&&a.isAfter(b)||low!=null&&(low.signum()<0||low.scale()>2)||high!=null&&(high.signum()<0||high.scale()>2)||low!=null&&high!=null&&low.compareTo(high)>0)throw new IllegalArgumentException();
   if(country!=null){if(!Set.of(Locale.getISOCountries()).contains(country))throw new IllegalArgumentException();where.append(" AND EXISTS (SELECT 1 FROM {h-schema}trip_locations l WHERE l.trip_id=t.id AND l.country_code=:country)");args.put("country",country);}
   if(style!=null){if(!ProfileService.STYLES.contains(style))throw new IllegalArgumentException();where.append(" AND :style = ANY(t.styles)");args.put("style",style);}
   if(a!=null){where.append(" AND t.date_to>=:from");args.put("from",a);}
   if(b!=null){where.append(" AND t.date_from<=:to");args.put("to",b);}
   if(low!=null){where.append(" AND t.budget_max>=:min");args.put("min",low);}
   if(high!=null){where.append(" AND t.budget_min<=:max");args.put("max",high);}
  }catch(RuntimeException e){throw new AuthProblem(400,"INVALID_SEARCH");}
  var em=Trip.getEntityManager().unwrap(org.hibernate.Session.class);
  var count=em.createNativeQuery("SELECT count(*)"+where,Long.class);
  var rows=em.createNativeQuery("SELECT t.*"+where+" ORDER BY t.published_at DESC,t.id DESC",Trip.class);
  args.forEach((k,v)->{count.setParameter(k,v);rows.setParameter(k,v);});
  rows.setFirstResult((page-1)*size);rows.setMaxResults(size);
  return new Results(rows.getResultList().stream().map(service::view).toList(),count.getSingleResult(),page,size);
 }
 public static String slug(String title){
  String s=java.text.Normalizer.normalize(title==null?"":title,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace("ł","l").replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");
  if(s.length()>140)s=s.substring(0,140).replaceAll("-$","");
  return s.isEmpty()?"trip":s;
 }
}
