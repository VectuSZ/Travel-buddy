package pl.travelbuddy.auth;
public class AuthProblem extends RuntimeException {
 public final int status;
 public final String code;
 public AuthProblem(int status, String code) { super(code); this.status=status; this.code=code; }
}
