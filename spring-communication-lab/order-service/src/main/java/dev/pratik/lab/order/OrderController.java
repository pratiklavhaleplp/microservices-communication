package dev.pratik.lab.order;

import static dev.pratik.lab.order.OrderDtos.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
  private final OrderStore store;
  private final InventoryClient inventory;
  private final PaymentClient payment;

  public OrderController(OrderStore store, InventoryClient inventory, PaymentClient payment) {
    this.store = store;
    this.inventory = inventory;
    this.payment = payment;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('SCOPE_orders.write')")
  public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest r) {
    OrderResponse o = store.create(r, inventory.product(r.sku()));
    return ResponseEntity.created(URI.create("/api/v1/orders/" + o.id())).body(o);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_orders.read')")
  public OrderResponse get(@PathVariable UUID id) {
    return store.get(id);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('SCOPE_orders.read')")
  public OrderPage page(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "createdAt") String sort) {
    return store.page(status, page, size, sort);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_orders.write')")
  public OrderResponse replace(@PathVariable UUID id, @Valid @RequestBody OrderRequest r) {
    return store.replace(id, r, inventory.product(r.sku()));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_orders.write')")
  public OrderResponse note(@PathVariable UUID id, @Valid @RequestBody NoteRequest r) {
    return store.note(id, r.note());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_orders.write')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    store.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/checkout")
  @PreAuthorize("hasAuthority('SCOPE_orders.write')")
  public ResponseEntity<OrderResponse> checkout(@PathVariable UUID id) {
    OrderResponse o = store.beginCheckout(id);
    if ("PAID".equals(o.status())) return ResponseEntity.ok(o);
    payment.charge(
        new ChargeRequest(o.id(), o.sku(), o.quantity(), o.amount(), o.currency()),
        "order-" + o.id());
    // Payment accepted; the durable event updates PAID. Never pretend that an HTTP timeout means a
    // failed charge.
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/orders/" + id))
        .body(store.get(id));
  }
}
