package dev.pratik.lab.order;

import dev.pratik.lab.common.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@Profile("lab")
@RequestMapping("/api/lab")
public class ClientLabController {
  private final RestClient rest;
  private final RestTemplate legacy;
  private final WebClient web;
  private final PaymentFeignClient feign;
  private final ServiceTokens tokens;
  private final CircuitBreaker breaker;
  private final String url;

  public ClientLabController(
      @Qualifier("comparisonRestClient") RestClient rest,
      RestTemplate legacy,
      WebClient web,
      PaymentFeignClient feign,
      ServiceTokens tokens,
      CircuitBreaker paymentBreaker,
      @Value("${lab.payment.url}") String url) {
    this.rest = rest;
    this.legacy = legacy;
    this.web = web;
    this.feign = feign;
    this.tokens = tokens;
    this.breaker = paymentBreaker;
    this.url = url;
  }

  @GetMapping("/clients/{kind}")
  public Map<?, ?> compare(@PathVariable String kind) {
    String token = tokens.accessToken(), id = CorrelationFilter.current();
    return switch (kind) {
      case "restclient" ->
          rest.get()
              .uri("/api/v1/payments/instance")
              .headers(
                  h -> {
                    h.setBearerAuth(token);
                    h.set(CorrelationFilter.HEADER, id);
                  })
              .retrieve()
              .body(Map.class);
      case "webclient" ->
          web.get()
              .uri("/api/v1/payments/instance")
              .headers(
                  h -> {
                    h.setBearerAuth(token);
                    h.set(CorrelationFilter.HEADER, id);
                  })
              .retrieve()
              .bodyToMono(Map.class)
              .contextCapture()
              .block();
      case "feign" -> feign.instance("Bearer " + token, id);
      case "resttemplate" -> {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set(CorrelationFilter.HEADER, id);
        yield legacy
            .exchange(
                url + "/api/v1/payments/instance",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class)
            .getBody();
      }
      default ->
          throw new ApiException(
              400, "UNKNOWN_CLIENT", "Use restclient, webclient, feign or resttemplate.");
    };
  }

  @GetMapping("/forward-token")
  public Map<?, ?> forward(@AuthenticationPrincipal Jwt inboundToken) {
    // Explicitly demonstrate forwarding. Main checkout instead uses the order-service machine
    // identity.
    return rest.get()
        .uri("/api/v1/payments/instance")
        .headers(
            h -> {
              h.setBearerAuth(inboundToken.getTokenValue());
              h.set(CorrelationFilter.HEADER, CorrelationFilter.current());
            })
        .retrieve()
        .body(Map.class);
  }

  @GetMapping("/circuit")
  public Map<String, Object> state() {
    return Map.of(
        "state",
        breaker.getState().name(),
        "failedCalls",
        breaker.getMetrics().getNumberOfFailedCalls(),
        "bufferedCalls",
        breaker.getMetrics().getNumberOfBufferedCalls());
  }

  @PostMapping("/circuit/reset")
  public Map<String, Object> reset() {
    breaker.reset();
    return state();
  }
}
