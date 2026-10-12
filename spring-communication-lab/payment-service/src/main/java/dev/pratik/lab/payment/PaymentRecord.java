package dev.pratik.lab.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentRecord {
  protected PaymentRecord() {}

  @Id private UUID id;

  @Column(nullable = false, unique = true, name = "idempotency_key", length = 100)
  private String idempotencyKey;

  @Column(nullable = false, unique = true, name = "order_id")
  private UUID orderId;

  @Column(nullable = false, length = 50)
  private String sku;

  @Column(nullable = false)
  private Integer quantity;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false, name = "created_at")
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public UUID getOrderId() {
    return orderId;
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

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
