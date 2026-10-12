package dev.pratik.lab.order;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.pratik.lab.order.OrderDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.reactive.function.client.WebClient;

class PaymentConsumerContractTest {
  @Test
  void consumerUnderstandsProviderContractAndSendsRequiredFields() throws Exception {
    var server = new WireMockServer(options().dynamicPort());
    server.start();
    try {
      String fixture =
          new String(
              new ClassPathResource("contracts/payment-v1-response.json")
                  .getInputStream()
                  .readAllBytes(),
              java.nio.charset.StandardCharsets.UTF_8);
      server.stubFor(
          post(urlEqualTo("/api/v1/payments"))
              .willReturn(
                  aResponse()
                      .withStatus(201)
                      .withHeader("Content-Type", "application/json")
                      .withBody(fixture)));
      var tokens = mock(ServiceTokens.class);
      when(tokens.accessToken()).thenReturn("contract-token");
      var c = new ResilienceConfiguration();
      var client =
          new PaymentClient(
              WebClient.builder().baseUrl(server.baseUrl()).build(),
              tokens,
              c.paymentRetry(RetryRegistry.ofDefaults()),
              c.paymentBreaker(CircuitBreakerRegistry.ofDefaults()),
              new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
      var order = UUID.fromString("22222222-2222-2222-2222-222222222222");
      var response =
          client.charge(
              new ChargeRequest(order, "BOOK-1", 1, new BigDecimal("500.00"), "INR"),
              "order-" + order);
      assertThat(response.orderId()).isEqualTo(order);
      assertThat(response.amount()).isEqualByComparingTo("500");
      assertThat(response.status()).isEqualTo("CAPTURED");
      server.verify(
          1,
          postRequestedFor(urlEqualTo("/api/v1/payments"))
              .withHeader("Idempotency-Key", equalTo("order-" + order))
              .withHeader("Authorization", equalTo("Bearer contract-token"))
              .withRequestBody(matchingJsonPath("$.sku", equalTo("BOOK-1")))
              .withRequestBody(matchingJsonPath("$.quantity", equalTo("1")))
              .withRequestBody(matchingJsonPath("$.currency", equalTo("INR"))));
    } finally {
      server.stop();
    }
  }
}
