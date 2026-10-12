package dev.pratik.lab.auth;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AuthorizationConfig {
  @Bean
  @Order(1)
  SecurityFilterChain oauthEndpoints(HttpSecurity http) throws Exception {
    var server = OAuth2AuthorizationServerConfigurer.authorizationServer();
    http.securityMatcher(server.getEndpointsMatcher())
        .with(server, c -> {})
        .authorizeHttpRequests(a -> a.anyRequest().authenticated());
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain otherEndpoints(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            a -> a.requestMatchers("/actuator/health/**").permitAll().anyRequest().denyAll())
        .build();
  }

  @Bean
  RegisteredClientRepository clients(
      @Value("${LAB_CLIENT_SECRET:lab-secret}") String labSecret,
      @Value("${ORDER_CLIENT_SECRET:order-secret}") String orderSecret,
      @Value("${VIEWER_CLIENT_SECRET:viewer-secret}") String viewerSecret) {
    return new InMemoryRegisteredClientRepository(
        client(
            "lab-client",
            labSecret,
            "orders.read",
            "orders.write",
            "payments.read",
            "payments.write",
            "inventory.read",
            "demo.admin"),
        client("order-service", orderSecret, "payments.read", "payments.write", "inventory.read"),
        client("viewer", viewerSecret, "orders.read", "payments.read", "inventory.read"));
  }

  private RegisteredClient client(String id, String secret, String... scopes) {
    var client =
        RegisteredClient.withId(id)
            .clientId(id)
            .clientSecret("{noop}" + secret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenSettings(
                TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(30)).build());
    for (String scope : scopes) client.scope(scope);
    return client.build();
  }

  @Bean
  JWKSource<SecurityContext> keys() throws GeneralSecurityException {
    // Ephemeral keys and in-memory registrations keep the lab small. Production needs durable key
    // rotation.
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    KeyPair kp = g.generateKeyPair();
    RSAKey key =
        new RSAKey.Builder((RSAPublicKey) kp.getPublic())
            .privateKey((RSAPrivateKey) kp.getPrivate())
            .keyID(UUID.randomUUID().toString())
            .build();
    return new ImmutableJWKSet<>(new JWKSet(key));
  }

  @Bean
  AuthorizationServerSettings settings(
      @Value("${AUTH_ISSUER:http://localhost:9000}") String issuer) {
    return AuthorizationServerSettings.builder().issuer(issuer).build();
  }

  @Bean
  OAuth2TokenCustomizer<JwtEncodingContext> audience() {
    return ctx -> ctx.getClaims().audience(List.of("communication-lab"));
  }
}
