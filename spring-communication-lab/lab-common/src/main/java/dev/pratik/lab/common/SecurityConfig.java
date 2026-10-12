package dev.pratik.lab.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  JwtDecoder jwtDecoder(
      @Value("${lab.auth.jwks}") String jwks, @Value("${lab.auth.issuer}") String issuer) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    OAuth2TokenValidator<Jwt> audience =
        jwt ->
            jwt.getAudience() != null && jwt.getAudience().contains("communication-lab")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Wrong audience", null));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), audience));
    return decoder;
  }

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http, ObjectMapper json) throws Exception {
    // This is a stateless, bearer-token API; no cookie/session login is used.
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.authorizeHttpRequests(
        a ->
            a.requestMatchers(
                    "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .permitAll()
                .requestMatchers("/actuator/**", "/api/lab/**")
                .hasAuthority("SCOPE_demo.admin")
                .anyRequest()
                .authenticated());
    http.oauth2ResourceServer(
        o ->
            o.jwt(Customizer.withDefaults())
                .authenticationEntryPoint(
                    (req, res, ex) -> {
                      res.setStatus(401);
                      res.setHeader("WWW-Authenticate", "Bearer");
                      res.setContentType("application/json");
                      json.writeValue(
                          res.getOutputStream(),
                          ErrorAdvice.body(
                              401,
                              "UNAUTHORIZED",
                              "Supply a valid bearer token.",
                              req.getRequestURI()));
                    }));
    http.exceptionHandling(
        e ->
            e.authenticationEntryPoint(
                    (req, res, ex) -> {
                      res.setStatus(401);
                      res.setHeader("WWW-Authenticate", "Bearer");
                      res.setContentType("application/json");
                      json.writeValue(
                          res.getOutputStream(),
                          ErrorAdvice.body(
                              401,
                              "UNAUTHORIZED",
                              "Supply a valid bearer token.",
                              req.getRequestURI()));
                    })
                .accessDeniedHandler(
                    (req, res, ex) -> {
                      res.setStatus(403);
                      res.setContentType("application/json");
                      json.writeValue(
                          res.getOutputStream(),
                          ErrorAdvice.body(
                              403, "FORBIDDEN", "Required scope is missing.", req.getRequestURI()));
                    }));
    return http.build();
  }
}
