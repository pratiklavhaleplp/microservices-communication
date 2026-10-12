package dev.pratik.lab.common;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// A versioned wire contract, never a JPA entity. Consumers validate schemaVersion.
public record PaymentCaptured(
    UUID eventId,
    int schemaVersion,
    UUID paymentId,
    UUID orderId,
    String sku,
    int quantity,
    BigDecimal amount,
    String currency,
    String correlationId,
    Instant occurredAt) {
  public void validate() {
    if (schemaVersion != 1
        || eventId == null
        || paymentId == null
        || orderId == null
        || sku == null
        || sku.isBlank()
        || sku.length() > 50
        || quantity < 1
        || amount == null
        || amount.signum() <= 0
        || !"INR".equals(currency)
        || occurredAt == null
        || correlationId == null
        || !correlationId.matches("[A-Za-z0-9._-]{1,100}"))
      throw new IllegalArgumentException("Invalid PaymentCaptured v1 event");
  }
}
