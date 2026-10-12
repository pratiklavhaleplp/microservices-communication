package dev.pratik.lab.payment;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {
  private PaymentDtos() {}

  public record ChargeRequest(
      @NotNull UUID orderId,
      @NotBlank @Size(max = 50) String sku,
      @Min(1) @Max(100) int quantity,
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
      @Pattern(regexp = "INR") @NotNull String currency) {}

  public record PaymentResponse(
      UUID id,
      UUID orderId,
      BigDecimal amount,
      String currency,
      String status,
      Instant createdAt) {}

  public record ChargeResult(PaymentResponse payment, boolean replayed) {}
}
