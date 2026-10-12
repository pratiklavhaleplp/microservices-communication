package dev.pratik.lab.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
  protected PurchaseOrder() {}

  @Id private UUID id;

  @Column(nullable = false, length = 50)
  private String sku;

  @Column(nullable = false)
  private Integer quantity;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, length = 200)
  private String note;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "payment_id")
  private UUID paymentId;

  @Column(nullable = false, name = "created_at")
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public String getNote() {
    return note;
  }

  public String getStatus() {
    return status;
  }

  public UUID getPaymentId() {
    return paymentId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
