package pl.travelbuddy.auth;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.scheduler.Scheduled;
import java.time.Instant;
import java.util.*;
@ApplicationScoped
public class GoogleAttempts {
 public record Attempt(String bindingHash, String locale, String terms, Instant acceptedAt, Instant expiresAt) {}
 private final Map<String,Attempt> pending=new HashMap<>();
 private final Map<String,String> states=new HashMap<>();
 private String hash(String raw) { return HexFormat.of().formatHex(Tokens.hash(raw)); }
 @Scheduled(every="1m") public synchronized void clean() {
  pending.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(Instant.now()));
  states.entrySet().removeIf(e -> !pending.containsKey(e.getValue()));
 }
 public synchronized String start(String locale, String terms) {
  clean(); if (pending.size()>=1000) throw new AuthProblem(429,"RATE_LIMITED");
  String raw=Tokens.random(), key=hash(raw);
  pending.put(key,new Attempt(key,locale,terms,terms==null?null:Instant.now(),Instant.now().plusSeconds(600)));
  return raw;
 }
 public synchronized void bind(String raw, String state) {
  clean(); if (!Tokens.valid(raw) || !pending.containsKey(hash(raw))) throw new AuthProblem(400,"GOOGLE_FLOW_INVALID");
  String key=hash(raw);
  if (states.containsValue(key)) throw new AuthProblem(400,"GOOGLE_FLOW_INVALID");
  states.put(state,key);
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
