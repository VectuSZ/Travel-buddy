package pl.travelbuddy.auth;
import io.quarkus.mailer.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
@ApplicationScoped
public class ResetMailer {
 @Inject Mailer mailer;
 @ConfigProperty(name="auth.frontend-url") String frontendUrl;
 public void send(User user, String token) {
  boolean en="en".equals(user.uiLocale);
  // Fragment is not sent in HTTP URLs, access logs or Referer headers.
  String url=frontendUrl+(en?"/en":"")+"/reset-password#token="+token;
  mailer.send(Mail.withText(user.email,en?"Reset your password":"Zresetuj hasło",
   (en?"Use this link within 30 minutes. If you did not request this, ignore this message.\n":"Użyj linku w ciągu 30 minut. Jeśli nie prosisz o reset, zignoruj tę wiadomość.\n")+url));
 }
}
