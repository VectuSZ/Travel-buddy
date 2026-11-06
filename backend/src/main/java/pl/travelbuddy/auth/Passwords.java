package pl.travelbuddy.auth;
import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;
import jakarta.enterprise.context.ApplicationScoped;
@ApplicationScoped
public class Passwords {
 private static final Argon2Function FUNCTION = Argon2Function.getInstance(19456, 2, 1, 32, Argon2.ID);
 private final String dummy = hash(Tokens.random());
 public String hash(String password) { return Password.hash(password).addRandomSalt(16).with(FUNCTION).getResult(); }
 public boolean verify(String password, String hash) { String encoded = hash == null ? dummy : hash; return Password.check(password, encoded).with(Argon2Function.getInstanceFromHash(encoded)) && hash != null; }
}
