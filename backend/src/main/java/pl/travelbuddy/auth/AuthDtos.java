package pl.travelbuddy.auth;
import jakarta.validation.constraints.*;
public final class AuthDtos {
 private AuthDtos() {}
 // Never add role/status/password hashes to these request or response contracts.
 public record Register(@NotBlank @Email @Size(max=254) String email,
  @NotNull @Size(min=12,max=1024) String password,
  @NotNull @Size(min=12,max=1024) String passwordConfirmation,
  @NotNull @Pattern(regexp="pl|en") String locale,
  @AssertTrue boolean acceptTerms, @NotNull @Pattern(regexp="local-draft-1") String termsVersion) {}
 public record Login(@NotBlank @Email @Size(max=254) String email, @NotNull @Size(min=12,max=1024) String password) {}
 public record ResetRequest(@NotBlank @Email @Size(max=254) String email) {}
 public record ResetConfirm(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,
  @NotNull @Size(min=12,max=1024) String password, @NotNull @Size(min=12,max=1024) String passwordConfirmation) {}
 public record Account(String email) {}
}
