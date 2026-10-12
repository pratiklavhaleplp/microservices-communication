package dev.pratik.lab.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** A deliberately different response shape; the existing v1 endpoint still works. */
@RestController
@RequestMapping("/api/v2/orders")
public class OrderV2Controller {
  private final OrderStore store;

  public OrderV2Controller(OrderStore store) {
    this.store = store;
  }

  public record Money(BigDecimal value, String currency) {}

  public record OrderV2(
      UUID id,
      String sku,
      int quantity,
      Money total,
      String note,
      String status,
      UUID paymentId,
      Instant createdAt) {}

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_orders.read')")
  public OrderV2 get(@PathVariable UUID id) {
    var o = store.get(id);
    return new OrderV2(
        o.id(),
        o.sku(),
        o.quantity(),
        new Money(o.amount(), o.currency()),
        o.note(),
        o.status(),
        o.paymentId(),
        o.createdAt());
  }
}
