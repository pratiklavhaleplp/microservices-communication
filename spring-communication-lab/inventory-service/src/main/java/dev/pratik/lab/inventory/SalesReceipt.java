package dev.pratik.lab.inventory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales_receipts")
public class SalesReceipt {
  protected SalesReceipt() {}

  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(nullable = false, name = "order_id", unique = true)
  private UUID orderId;

  @Column(nullable = false, name = "payment_id", unique = true)
  private UUID paymentId;

  @Column(nullable = false, length = 50)
  private String sku;

  @Column(nullable = false)
  private Integer quantity;

  @Column(nullable = false, name = "received_at")
  private Instant receivedAt;

  public UUID getEventId() {
    return eventId;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public UUID getPaymentId() {
    return paymentId;
  }

  public String getSku() {
    return sku;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }
}
