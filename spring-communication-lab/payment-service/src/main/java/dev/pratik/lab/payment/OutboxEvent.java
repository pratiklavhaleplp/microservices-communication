package dev.pratik.lab.payment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
  protected OutboxEvent() {}

  @Id private UUID id;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Column(length = 100)
  private String traceparent;

  @Column(nullable = false, name = "correlation_id", length = 100)
  private String correlationId;

  @Column(nullable = false)
  private Boolean sent;

  @Column(nullable = false)
  private Integer attempts;

  @Column(nullable = false, name = "created_at")
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public String getPayload() {
    return payload;
  }

  public String getTraceparent() {
    return traceparent;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public Boolean getSent() {
    return sent;
  }

  public Integer getAttempts() {
    return attempts;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
