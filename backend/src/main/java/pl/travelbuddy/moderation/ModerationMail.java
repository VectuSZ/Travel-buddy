package pl.travelbuddy.moderation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import io.quarkus.mailer.*;
@ApplicationScoped
public class ModerationMail {
 public record Notice(String email,boolean english,String action,String reason){}
 @Inject Mailer mailer;
 public void send(@Observes(during=TransactionPhase.AFTER_SUCCESS) Notice notice){
  try{mailer.send(Mail.withText(notice.email(),notice.english()?"Travel Buddy moderation decision":"Decyzja moderacji Travel Buddy",
   (notice.english()?"Decision: ":"Decyzja: ")+notice.action()+"\n"+(notice.english()?"Reason: ":"Uzasadnienie: ")+notice.reason()));}
  catch(RuntimeException e){org.jboss.logging.Logger.getLogger(ModerationMail.class).warn("Moderation notification delivery failed");}
 }
}
