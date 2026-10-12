package dev.pratik.lab.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pratik.lab.common.*;
import io.micrometer.tracing.*;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.hibernate.Session;
import org.slf4j.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxRelay {
  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  @PersistenceContext private EntityManager em;
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper json;
  private final TransactionTemplate tx;
  private final Propagator propagator;
  private final Tracer tracer;

  public OutboxRelay(
      KafkaTemplate<String, String> kafka,
      ObjectMapper json,
      TransactionTemplate tx,
      Propagator propagator,
      Tracer tracer) {
    this.kafka = kafka;
    this.json = json;
    this.tx = tx;
    this.propagator = propagator;
    this.tracer = tracer;
  }

  @Scheduled(fixedDelayString = "${lab.outbox.delay-ms:1000}")
  public void publishPending() {
    try {
      tx.executeWithoutResult(
          status -> {
            List<OutboxEvent> rows =
                em.createQuery(
                        "select e from OutboxEvent e where e.sent=false order by e.createdAt",
                        OutboxEvent.class)
                    .setMaxResults(10)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setHint("jakarta.persistence.lock.timeout", -2)
                    .getResultList();
            for (OutboxEvent row : rows) publish(row);
          });
    } catch (Exception e) {
      log.warn("Outbox batch will retry: {}", e.getClass().getSimpleName());
    }
  }

  private void publish(OutboxEvent row) {
    // Restore the HTTP parent saved in the DB; the relay runs long after that HTTP thread finished.
    Span span =
        propagator
            .extract(
                row, (carrier, key) -> "traceparent".equals(key) ? carrier.getTraceparent() : null)
            .name("outbox.publish")
            .start();
    try (var scope = tracer.withSpan(span);
        var ignored = MDC.putCloseable("correlationId", row.getCorrelationId())) {
      PaymentCaptured event = json.readValue(row.getPayload(), PaymentCaptured.class);
      // Key by order ID: records for one order use the same partition.
      ProducerRecord<String, String> record =
          new ProducerRecord<>(
              KafkaConfiguration.PAYMENTS, event.orderId().toString(), row.getPayload());
      record
          .headers()
          .add(CorrelationFilter.HEADER, row.getCorrelationId().getBytes(StandardCharsets.UTF_8));
      // KafkaTemplate observation injects traceparent from the restored span above.
      // Wait for the broker ACK before marking sent; this is not a consumer-completion ACK.
      kafka.send(record).get(12, TimeUnit.SECONDS);
      em.unwrap(Session.class)
          .createMutationQuery(
              "update OutboxEvent e set e.sent=true,e.attempts=e.attempts+1 where e.id=:id")
          .setParameter("id", row.getId())
          .executeUpdate();
    } catch (Exception ex) {
      if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
      span.error(ex);
      em.unwrap(Session.class)
          .createMutationQuery("update OutboxEvent e set e.attempts=e.attempts+1 where e.id=:id")
          .setParameter("id", row.getId())
          .executeUpdate();
      log.warn("Event {} still pending: {}", row.getId(), ex.getClass().getSimpleName());
    } finally {
      span.end();
    }
  }
}
