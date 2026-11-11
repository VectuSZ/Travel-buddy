package pl.travelbuddy.auth;
import io.quarkus.oidc.*;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.annotation.Priority;
@ApplicationScoped @Alternative @Priority(1)
public class DiscardOidcTokens implements TokenStateManager {
 public Uni<String> createTokenState(RoutingContext r, OidcTenantConfig c, AuthorizationCodeTokens t, OidcRequestContext<String> x) {
  return Uni.createFrom().item("discarded");
 }
 public Uni<AuthorizationCodeTokens> getTokens(RoutingContext r, OidcTenantConfig c, String t, OidcRequestContext<AuthorizationCodeTokens> x) {
  return Uni.createFrom().nullItem();
 }
 public Uni<Void> deleteTokens(RoutingContext r, OidcTenantConfig c, String t, OidcRequestContext<Void> x) { return Uni.createFrom().voidItem(); }
}
