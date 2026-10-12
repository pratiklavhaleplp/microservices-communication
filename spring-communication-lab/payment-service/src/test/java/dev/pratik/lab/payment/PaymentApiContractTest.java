package dev.pratik.lab.payment;

import static dev.pratik.lab.payment.PaymentDtos.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.pratik.lab.common.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, ErrorAdvice.class, CorrelationFilter.class})
class PaymentApiContractTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean PaymentStore store;
  @MockitoBean FaultInjector faults;
  String request =
      "{\"orderId\":\"22222222-2222-2222-2222-222222222222\",\"sku\":\"BOOK-1\",\"quantity\":1,\"amount\":500.00,\"currency\":\"INR\"}";

  @Test
  void providerMatchesPublishedResponseContract() throws Exception {
    var payment =
        new PaymentResponse(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            new BigDecimal("500.00"),
            "INR",
            "CAPTURED",
            Instant.parse("2026-01-01T00:00:00Z"));
    when(store.charge(anyString(), any(), anyString()))
        .thenReturn(new ChargeResult(payment, false));
    String contract =
        new String(
            new ClassPathResource("contracts/payment-v1-response.json")
                .getInputStream()
                .readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);
    mvc.perform(
            post("/api/v1/payments")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments.write")))
                .header("Idempotency-Key", "contract-key")
                .header("X-Correlation-ID", "contract-1")
                .contentType("application/json")
                .content(request))
        .andExpect(status().isCreated())
        .andExpect(header().string("Idempotency-Replayed", "false"))
        .andExpect(header().string("X-Correlation-ID", "contract-1"))
        .andExpect(content().json(contract));
  }

  @Test
  void missingTokenIs401() throws Exception {
    mvc.perform(get("/api/v1/payments/instance"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void missingWriteScopeIs403() throws Exception {
    mvc.perform(
            post("/api/v1/payments")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments.read")))
                .header("Idempotency-Key", "contract")
                .contentType("application/json")
                .content(request))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidJsonIs400BeforeBusinessCodeRuns() throws Exception {
    mvc.perform(
            post("/api/v1/payments")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments.write")))
                .header("Idempotency-Key", "contract")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verifyNoInteractions(store);
  }
}
