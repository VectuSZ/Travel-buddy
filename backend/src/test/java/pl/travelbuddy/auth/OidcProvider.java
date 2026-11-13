package pl.travelbuddy.auth;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import com.sun.net.httpserver.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Protocol fixture: Quarkus (not the test) verifies JWT and the authorization flow. */
public class OidcProvider implements QuarkusTestResourceLifecycleManager {
 static HttpServer server;
 static String issuer;
 static KeyPair key;
 static final ObjectMapper JSON=new ObjectMapper();
 record Grant(Map<String,Object> claims,String challenge) {}
 static final Map<String,Grant> grants=new ConcurrentHashMap<>();
 static String encode(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
 static String unsigned(java.math.BigInteger n) { byte[] b=n.toByteArray(); return encode(b[0]==0?Arrays.copyOfRange(b,1,b.length):b); }
 static Map<String,String> query(String q) {
  Map<String,String> m=new HashMap<>(); if(q!=null) for(String p:q.split("&")) { String[] kv=p.split("=",2); m.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),kv.length>1?URLDecoder.decode(kv[1],StandardCharsets.UTF_8):""); } return m;
 }
 static String grant(Map<String,Object> claims,String challenge) { String c=UUID.randomUUID().toString(); grants.put(c,new Grant(claims,challenge)); return c; }
 static void reply(HttpExchange e,int status,Object body) throws java.io.IOException { byte[] b=JSON.writeValueAsBytes(body); e.getResponseHeaders().set("Content-Type","application/json"); e.sendResponseHeaders(status,b.length); e.getResponseBody().write(b); e.close(); }
 public Map<String,String> start() {
  try {
   var gen=KeyPairGenerator.getInstance("RSA"); gen.initialize(2048); key=gen.generateKeyPair();
   server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); issuer="http://127.0.0.1:"+server.getAddress().getPort();
   server.createContext("/.well-known/openid-configuration",e -> reply(e,200,Map.of("issuer",issuer,"authorization_endpoint",issuer+"/authorize","token_endpoint",issuer+"/token","jwks_uri",issuer+"/jwks","response_types_supported",List.of("code"),"subject_types_supported",List.of("public"),"id_token_signing_alg_values_supported",List.of("RS256"),"code_challenge_methods_supported",List.of("S256"))));
   server.createContext("/jwks",e -> { var pub=(RSAPublicKey)key.getPublic(); reply(e,200,Map.of("keys",List.of(Map.of("kty","RSA","kid","test-key","use","sig","alg","RS256","n",unsigned(pub.getModulus()),"e",unsigned(pub.getPublicExponent()))))); });
   server.createContext("/token",e -> {
    try {
     var params=query(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
     Grant g=grants.remove(params.get("code"));
     if(g==null || !encode(MessageDigest.getInstance("SHA-256").digest(params.getOrDefault("code_verifier","").getBytes(StandardCharsets.US_ASCII))).equals(g.challenge())) { reply(e,400,Map.of("error","invalid_grant")); return; }
     String input=encode(JSON.writeValueAsBytes(Map.of("alg","RS256","kid","test-key")))+"."+encode(JSON.writeValueAsBytes(g.claims()));
     var sig=Signature.getInstance("SHA256withRSA"); sig.initSign(key.getPrivate()); sig.update(input.getBytes(StandardCharsets.US_ASCII));
     reply(e,200,Map.of("id_token",input+"."+encode(sig.sign()),"access_token","provider-access-secret","refresh_token","provider-refresh-secret","token_type","Bearer","expires_in",300));
    } catch(Exception ex) { reply(e,400,Map.of("error","invalid_grant")); }
   });
   server.start();
   return Map.of("%test.auth.google.enabled","true","quarkus.oidc.tenant-enabled","true","quarkus.oidc.auth-server-url",issuer,
    "quarkus.oidc.token.issuer",issuer,"quarkus.oidc.client-id","test-client","quarkus.oidc.token.audience","test-client",
    "quarkus.oidc.credentials.secret","test-only-secret-with-more-than-32-chars",
    "quarkus.oidc.authentication.redirect-path","http://localhost:8081/api/v1/auth/google/callback");
  } catch(Exception e) { throw new RuntimeException(e); }
 }
 public void stop() { server.stop(0); grants.clear(); }
}
