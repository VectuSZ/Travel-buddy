package pl.travelbuddy.moderation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.annotation.PostConstruct;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.runtime.LaunchMode;
import pl.travelbuddy.auth.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.*;
import java.net.*;
import java.net.http.*;
@ApplicationScoped
public class ReportChallenge {
 @ConfigProperty(name="reports.challenge.local",defaultValue="false") boolean local;
 @ConfigProperty(name="reports.turnstile.secret",defaultValue="disabled") String secret;
 @ConfigProperty(name="reports.turnstile.site-key",defaultValue="disabled") String siteKey;
 @ConfigProperty(name="auth.frontend-url") String frontend;
 @Inject ObjectMapper json;
 private final Map<String,Instant> tokens=new HashMap<>();
 @PostConstruct void guard(){if(local&&LaunchMode.current()==LaunchMode.NORMAL)throw new IllegalStateException("Local challenge forbidden outside dev/test");}
 public Map<String,Object> config(){return Map.of("local",local,"siteKey",siteKey);}
 public synchronized String issue(){
  if(!local)throw new AuthProblem(404,"NOT_FOUND");
  tokens.entrySet().removeIf(e->e.getValue().isBefore(Instant.now()));
  if(tokens.size()>=1000)throw new AuthProblem(429,"RATE_LIMITED");
  String token=Tokens.random();tokens.put(token,Instant.now().plusSeconds(300));return token;
 }
 public void verify(String token){
  if(token==null||token.length()>2048)throw new AuthProblem(400,"CHALLENGE_INVALID");
  if(local){synchronized(this){Instant expiry=tokens.remove(token);if(expiry!=null&&expiry.isAfter(Instant.now()))return;}throw new AuthProblem(400,"CHALLENGE_INVALID");}
  if(secret.equals("disabled")||siteKey.equals("disabled"))throw new AuthProblem(503,"CHALLENGE_UNAVAILABLE");
  try{
   var request=HttpRequest.newBuilder(URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify")).timeout(Duration.ofSeconds(8)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("secret="+URLEncoder.encode(secret,java.nio.charset.StandardCharsets.UTF_8)+"&response="+URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8))).build();
   var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(request,HttpResponse.BodyHandlers.ofString());
   var result=json.readTree(response.body());
   if(response.statusCode()==200&&result.path("success").asBoolean()&&"report".equals(result.path("action").asText())&&URI.create(frontend).getHost().equals(result.path("hostname").asText()))return;
  }catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception ignored){}
  throw new AuthProblem(400,"CHALLENGE_INVALID");
 }
}
