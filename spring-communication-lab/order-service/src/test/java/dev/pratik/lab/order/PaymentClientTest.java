package dev.pratik.lab.order;

import static dev.pratik.lab.order.OrderDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.*;
import java.math.BigDecimal;
import java.util.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

class PaymentClientTest {
  MockWebServer server;
  PaymentClient client;
  Retry retry;
  CircuitBreaker breaker;
  ChargeRequest req =
      new ChargeRequest(UUID.randomUUID(), "BOOK-1", 1, new BigDecimal("500.00"), "INR");

  @BeforeEach
  void setup() throws Exception {
    server = new MockWebServer();
    server.start();
    var tokens = mock(ServiceTokens.class);
    when(tokens.accessToken()).thenReturn("test-token");
    var config = new ResilienceConfiguration();
    retry = config.paymentRetry(RetryRegistry.ofDefaults());
    breaker = config.paymentBreaker(CircuitBreakerRegistry.ofDefaults());
    client =
        new PaymentClient(
            WebClient.builder().baseUrl(server.url("/").toString()).build(),
            tokens,
            retry,
            breaker,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
  }

  @AfterEach
  void close() throws Exception {
    server.shutdown();
  }

  MockResponse ok() {
    return new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            "{\"id\":\""
                + UUID.randomUUID()
                + "\",\"orderId\":\""
                + req.orderId()
                + "\",\"amount\":500.00,\"currency\":\"INR\",\"status\":\"CAPTURED\",\"createdAt\":\"2026-01-01T00:00:00Z\"}");
  }

  @Test
  void retries503ThenReturnsSuccessAndKeepsSameKey() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(503));
    server.enqueue(ok());
    assertThat(client.charge(req, "stable-key").status()).isEqualTo("CAPTURED");
    assertThat(server.getRequestCount()).isEqualTo(2);
    for (int i = 0; i < 2; i++) {
      var sent = server.takeRequest();
      assertThat(sent.getHeader("Idempotency-Key")).isEqualTo("stable-key");
      assertThat(sent.getHeader("Authorization")).isEqualTo("Bearer test-token");
    }
  }

  @Test
  void doesNotRetry400Or429() {
    for (int status : List.of(400, 429)) {
      int before = server.getRequestCount();
      server.enqueue(new MockResponse().setResponseCode(status).setHeader("Retry-After", "2"));
      assertThatThrownBy(() -> client.charge(req, "key")).isInstanceOf(DownstreamFailure.class);
      assertThat(server.getRequestCount() - before).isEqualTo(1);
    }
  }

  @Test
  void malformedSuccessBodyIsAContractFailureAndIsNotRetried() {
    server.enqueue(
        new MockResponse().setHeader("Content-Type", "application/json").setBody("not-json"));
    assertThatThrownBy(() -> client.charge(req, "key"))
        .isInstanceOf(DownstreamFailure.class)
        .extracting(e -> ((DownstreamFailure) e).retryable())
        .isEqualTo(false);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void circuitOpensAfterFourFailedCheckoutsAndShortCircuitsTheFifth() {
    for (int i = 0; i < 12; i++) server.enqueue(new MockResponse().setResponseCode(503));
    for (int i = 0; i < 4; i++)
      assertThatThrownBy(() -> client.charge(req, "key")).isInstanceOf(DownstreamFailure.class);
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThatThrownBy(() -> client.charge(req, "key"))
        .isInstanceOf(CallNotPermittedException.class);
    assertThat(server.getRequestCount()).isEqualTo(12);
  }

  @Test
  void halfOpenProbeCanCloseCircuit() {
    breaker.transitionToOpenState();
    breaker.transitionToHalfOpenState();
    server.enqueue(ok());
    client.charge(req, "key");
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  @Test
  void totalDeadlineBoundsSlowResponses() {
    retry = Retry.of("once", RetryConfig.custom().maxAttempts(1).build());
    var tokens = mock(ServiceTokens.class);
    when(tokens.accessToken()).thenReturn("test");
    client =
        new PaymentClient(
            WebClient.builder().baseUrl(server.url("/").toString()).build(),
            tokens,
            retry,
            breaker,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    server.enqueue(ok().setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS));
    long start = System.nanoTime();
    assertThatThrownBy(() -> client.charge(req, "key")).isInstanceOf(DownstreamFailure.class);
    assertThat(java.time.Duration.ofNanos(System.nanoTime() - start).toMillis())
        .isBetween(1500L, 2900L);
  }
}
