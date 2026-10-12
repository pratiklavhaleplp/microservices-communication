package dev.pratik.lab.order;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class OrderDtos {
  private OrderDtos() {}

  public record OrderRequest(
      @NotBlank @Pattern(regexp = "[A-Z0-9-]{1,50}") String sku,
      @Min(1) @Max(100) int quantity,
      @NotNull @Size(max = 200) String note) {}

  public record NoteRequest(@NotNull @Size(max = 200) String note) {}

  public record OrderResponse(
      UUID id,
      String sku,
      int quantity,
      BigDecimal amount,
      String currency,
      String note,
      String status,
      UUID paymentId,
      Instant createdAt) {}

  public record OrderPage(List<OrderResponse> items, int page, int size, long total) {}

  // Consumer-owned DTOs deliberately do not depend on the Payment/Inventory modules or their
  // entities.
  public record ProductResponse(String sku, String name, BigDecimal price, String currency) {}

  public record ChargeRequest(
      UUID orderId, String sku, int quantity, BigDecimal amount, String currency) {}

  public record PaymentResponse(
      UUID id,
      UUID orderId,
      BigDecimal amount,
      String currency,
      String status,
      Instant createdAt) {}
}
