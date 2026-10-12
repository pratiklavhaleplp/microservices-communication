package dev.pratik.lab.order;

import static dev.pratik.lab.order.OrderDtos.*;

import dev.pratik.lab.common.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.time.Duration;
import org.slf4j.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

@Component
public class PaymentClient {
  private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);
  private final io.micrometer.core.instrument.MeterRegistry meters;
  private final WebClient client;
  private final ServiceTokens tokens;
  private final Retry retry;
  private final CircuitBreaker breaker;

  public PaymentClient(
      WebClient paymentWebClient,
      ServiceTokens tokens,
      Retry paymentRetry,
      CircuitBreaker paymentBreaker,
      io.micrometer.core.instrument.MeterRegistry meters) {
    this.meters = meters;
    this.client = paymentWebClient;
    this.tokens = tokens;
    this.retry = paymentRetry;
    this.breaker = paymentBreaker;
  }

  public PaymentResponse charge(ChargeRequest request, String idempotencyKey) {
    // Capture servlet-thread values once, before execution moves to Reactor worker threads.
    String token = tokens.accessToken(), correlationId = CorrelationFilter.current();
    // Circuit counts one checkout after retries are exhausted. OPEN rejects without hitting
    // Payment.
    return breaker.executeSupplier(
        () -> retry.executeSupplier(() -> call(request, idempotencyKey, token, correlationId)));
  }

  private PaymentResponse call(
      ChargeRequest request, String key, String token, String correlationId) {
    log.info("Calling Payment, order={}, idempotencyKey={}", request.orderId(), key);
    meters.counter("lab.payment.attempts").increment();
    return client
        .post() // POST creates a simulated payment.
        .uri("/api/v1/payments") // Provider endpoint.
        .contentType(MediaType.APPLICATION_JSON) // Our body is JSON.
        .accept(MediaType.APPLICATION_JSON) // Ask for a JSON response.
        .headers(
            h -> {
              h.setBearerAuth(token);
              h.set("Idempotency-Key", key);
              h.set(CorrelationFilter.HEADER, correlationId);
            })
        .bodyValue(request) // Jackson serializes this DTO.
        .retrieve() // Describe how to receive the response.
        .onStatus(
            s -> s.isError(),
            res -> {
              int status = res.statusCode().value();
              String after = res.headers().asHttpHeaders().getFirst("Retry-After");
              return res.releaseBody().then(Mono.error(DownstreamFailure.response(status, after)));
            })
        .bodyToMono(PaymentResponse.class) // Jackson deserializes the response DTO.
        .switchIfEmpty(
            Mono.error(
                new DownstreamFailure(200, false, null, "Payment returned an empty success body.")))
        .timeout(Duration.ofSeconds(2)) // Total per-attempt reactive deadline.
        .onErrorMap(
            e -> !(e instanceof DownstreamFailure),
            e -> {
              if (isTimeout(e)) meters.counter("lab.payment.timeouts").increment();
              return (e instanceof WebClientRequestException || isTimeout(e))
                  ? DownstreamFailure.transport(e)
                  : new DownstreamFailure(
                      0, false, null, "Payment response did not match the expected contract.");
            })
        .contextCapture() // Preserve observation context across threads.
        .block(); // Intentional bridge: this application uses Spring MVC.
  }

  private boolean isTimeout(Throwable e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof java.util.concurrent.TimeoutException
          || cause.getClass().getSimpleName().contains("Timeout")) return true;
    }
    return false;
  }
}
