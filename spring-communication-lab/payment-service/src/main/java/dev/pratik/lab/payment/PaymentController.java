package dev.pratik.lab.payment;

import static dev.pratik.lab.payment.PaymentDtos.*;

import dev.pratik.lab.common.CorrelationFilter;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
  private final PaymentStore store;
  private final FaultInjector faults;
  private final String instance;

  public PaymentController(
      PaymentStore store,
      FaultInjector faults,
      @Value("${INSTANCE_ID:payment-1}") String instance) {
    this.store = store;
    this.faults = faults;
    this.instance = instance;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('SCOPE_payments.write')")
  public ResponseEntity<PaymentResponse> charge(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ChargeRequest req) {
    String mode = faults.before();
    ChargeResult result = store.charge(key, req, CorrelationFilter.current());
    faults.after(
        mode); // Deliberately after DB commit: the caller may time out even when payment succeeded.
    return ResponseEntity.status(result.replayed() ? 200 : 201)
        .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
        .header("X-Instance-ID", instance)
        .body(result.payment());
  }

  @GetMapping("/by-order/{id}")
  @PreAuthorize("hasAuthority('SCOPE_payments.read')")
  public PaymentResponse byOrder(@PathVariable UUID id) {
    return store.byOrder(id);
  }

  @GetMapping("/instance")
  @PreAuthorize("hasAuthority('SCOPE_payments.read')")
  public Map<String, String> instance() {
    return Map.of("instance", instance, "correlationId", CorrelationFilter.current());
  }
}
