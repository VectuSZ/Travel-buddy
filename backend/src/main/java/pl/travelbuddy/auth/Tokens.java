package pl.travelbuddy.auth;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
public final class Tokens {
 private static final SecureRandom RANDOM = new SecureRandom();
 private Tokens() {}
 public static String random() { byte[] b = new byte[32]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
 public static byte[] hash(String token) {
  try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)); }
  catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
 }
 public static boolean valid(String token) { return token != null && token.matches("[A-Za-z0-9_-]{43}"); }
}
