package dev.pratik.lab.payment;

import static dev.pratik.lab.payment.PaymentDtos.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pratik.lab.common.*;
import io.micrometer.tracing.Tracer;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PaymentStore {
  @PersistenceContext private EntityManager em;
  private final ObjectMapper json;
  private final Tracer tracer;

  public PaymentStore(ObjectMapper json, Tracer tracer) {
    this.json = json;
    this.tracer = tracer;
  }

  @Transactional
  public ChargeResult charge(String key, ChargeRequest request, String correlationId) {
    if (key == null || !key.matches("[A-Za-z0-9._-]{1,100}"))
      throw new ApiException(
          400,
          "INVALID_IDEMPOTENCY_KEY",
          "Use 1-100 letters, digits, dots, underscores or hyphens.");
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    // A database uniqueness constraint arbitrates concurrent retries, across every service
    // instance.
    // This read is only an optimization. ON CONFLICT and unique indexes provide concurrency safety.
    boolean exists =
        em.createQuery(
                    "select count(p) from PaymentRecord p where p.idempotencyKey=:key", Long.class)
                .setParameter("key", key)
                .getSingleResult()
            > 0;
    int created =
        exists
            ? 0
            : em.unwrap(Session.class)
                .createMutationQuery(
                    """
insert into PaymentRecord(id,idempotencyKey,orderId,sku,quantity,amount,currency,status,createdAt)
values (:id,:key,:order,:sku,:qty,:amount,:currency,'CAPTURED',:at)
on conflict (idempotencyKey) do nothing
""")
                .setParameter("id", id)
                .setParameter("key", key)
                .setParameter("order", request.orderId())
                .setParameter("sku", request.sku())
                .setParameter("qty", request.quantity())
                .setParameter("amount", request.amount())
                .setParameter("currency", request.currency())
                .setParameter("at", now)
                .executeUpdate();
    PaymentRecord payment =
        em.createQuery(
                "select p from PaymentRecord p where p.idempotencyKey=:key", PaymentRecord.class)
            .setParameter("key", key)
            .getSingleResult();
    if (!payment.getOrderId().equals(request.orderId())
        || !payment.getSku().equals(request.sku())
        || payment.getQuantity() != request.quantity()
        || payment.getAmount().compareTo(request.amount()) != 0
        || !payment.getCurrency().equals(request.currency()))
      throw new ApiException(
          409, "IDEMPOTENCY_CONFLICT", "This key was already used with a different request.");
    if (created == 1) {
      PaymentCaptured event =
          new PaymentCaptured(
              UUID.randomUUID(),
              1,
              id,
              request.orderId(),
              request.sku(),
              request.quantity(),
              request.amount(),
              request.currency(),
              correlationId,
              now);
      String payload;
      try {
        payload = json.writeValueAsString(event);
      } catch (Exception e) {
        throw new IllegalStateException("Event serialization failed", e);
      }
      String parent = null;
      if (tracer.currentSpan() != null) {
        var ctx = tracer.currentSpan().context();
        parent =
            "00-"
                + ctx.traceId()
                + "-"
                + ctx.spanId()
                + "-"
                + (Boolean.TRUE.equals(ctx.sampled()) ? "01" : "00");
      }
      // Same PostgreSQL transaction as the payment. Either both rows commit, or neither does.
      em.unwrap(Session.class)
          .createMutationQuery(
              """
              insert into OutboxEvent(id,payload,traceparent,correlationId,sent,attempts,createdAt)
              values (:id,:payload,:parent,:correlation,false,0,:at)
              """)
          .setParameter("id", event.eventId())
          .setParameter("payload", payload)
          .setParameter("parent", parent)
          .setParameter("correlation", correlationId)
          .setParameter("at", now)
          .executeUpdate();
    }
    return new ChargeResult(view(payment), created == 0);
  }

  @Transactional(readOnly = true)
  public PaymentResponse byOrder(UUID id) {
    return em.createQuery("select p from PaymentRecord p where p.orderId=:id", PaymentRecord.class)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .map(this::view)
        .orElseThrow(
            () -> new ApiException(404, "PAYMENT_NOT_FOUND", "No payment exists for this order."));
  }

  @Transactional(readOnly = true)
  public long paymentCount(UUID order) {
    return em.createQuery("select count(p) from PaymentRecord p where p.orderId=:id", Long.class)
        .setParameter("id", order)
        .getSingleResult();
  }

  private PaymentResponse view(PaymentRecord p) {
    return new PaymentResponse(
        p.getId(), p.getOrderId(), p.getAmount(), p.getCurrency(), p.getStatus(), p.getCreatedAt());
  }
}
