package pl.travelbuddy.auth;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.scheduler.Scheduled;
import java.time.Instant;
import java.util.*;
@ApplicationScoped
public class GoogleAttempts {
 public record Attempt(String bindingHash, String locale, Instant startedAt, Instant expiresAt, String sessionToken, boolean linking) {}
 public record Registration(String issuer, String subject, String email, Attempt attempt, String confirmation, LinkTarget target) {}
 public record LinkTarget(UUID userId,String passwordRevision) {}
 private final Map<String,Registration> registrations=new HashMap<>();
 private final Map<String,Attempt> pending=new HashMap<>();
 private final Map<String,String> states=new HashMap<>();
 private String hash(String raw) { return HexFormat.of().formatHex(Tokens.hash(raw)); }
 @Scheduled(every="1m") public void clean() { clean(Instant.now()); }
 synchronized void clean(Instant now) {
  pending.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(now));
  registrations.values().removeIf(r -> !r.attempt().expiresAt().isAfter(now));
  states.entrySet().removeIf(e -> !pending.containsKey(e.getValue()));
 }
 public synchronized String start(String locale, String sessionToken, boolean linking) {
  clean();
  if (pending.size()+registrations.size()>=1000) throw new AuthProblem(429,"RATE_LIMITED");
  String raw=Tokens.random(), key=hash(raw);
  Instant now=Instant.now();
  pending.put(key,new Attempt(key,locale,now,now.plusSeconds(600),sessionToken,linking));
  return raw;
 }
 public synchronized void awaitConsent(Attempt attempt,String issuer,String subject,String email,LinkTarget target) {
  registrations.put(attempt.bindingHash(),new Registration(issuer,subject,email,attempt,Tokens.random(),target));
 }
 public synchronized Registration pendingDecision(String raw,boolean linking) {
  clean();
  Registration r=Tokens.valid(raw)?registrations.get(hash(raw)):null;
  return r!=null && (r.target()!=null)==linking ? r : null;
 }
 public synchronized Registration consumeRegistration(String raw,String confirmation,boolean linking) {
  var r=pendingDecision(raw,linking);
  if (r==null || !r.confirmation().equals(confirmation)) throw new AuthProblem(400,"GOOGLE_FLOW_INVALID");
  return registrations.remove(hash(raw));
 }
 public synchronized Attempt bind(String raw, String state) {
  clean(); if (!Tokens.valid(raw) || !pending.containsKey(hash(raw))) throw new AuthProblem(400,"GOOGLE_FLOW_INVALID");
  String key=hash(raw);
  if (states.containsValue(key)) throw new AuthProblem(400,"GOOGLE_FLOW_INVALID");
  states.put(state,key);
  return pending.get(key);
 }
 public synchronized String locale(String raw) {
  clean(); Attempt a=Tokens.valid(raw)?pending.get(hash(raw)):null;
  return a==null?"pl":a.locale();
 }
 public synchronized Attempt consume(String raw, String state) {
  clean(); if (!Tokens.valid(raw) || state==null) throw new AuthProblem(400,"GOOGLE_FLOW_INVALID");
  String key=states.get(state);
  if (!hash(raw).equals(key)) throw new AuthProblem(400,"GOOGLE_FLOW_INVALID");
  states.remove(state); Attempt attempt=pending.remove(key);
  if (attempt==null) throw new AuthProblem(400,"GOOGLE_FLOW_INVALID");
  return attempt;
 }
}
