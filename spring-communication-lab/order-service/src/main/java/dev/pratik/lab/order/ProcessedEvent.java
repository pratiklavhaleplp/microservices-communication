package dev.pratik.lab.order;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {
  protected ProcessedEvent() {}

  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(nullable = false, name = "processed_at")
  private Instant processedAt;

  public UUID getEventId() {
    return eventId;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
