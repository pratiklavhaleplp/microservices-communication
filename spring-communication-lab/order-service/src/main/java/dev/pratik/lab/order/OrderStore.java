package dev.pratik.lab.order;

import static dev.pratik.lab.order.OrderDtos.*;

import dev.pratik.lab.common.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OrderStore {
  @PersistenceContext private EntityManager em;

  @Transactional
  public OrderResponse create(OrderRequest r, ProductResponse product) {
    UUID id = UUID.randomUUID();
    em.unwrap(Session.class)
        .createMutationQuery(
            """
            insert into PurchaseOrder(id,sku,quantity,amount,currency,note,status,createdAt)
            values (:id,:sku,:qty,:amount,:currency,:note,'DRAFT',:at)
            """)
        .setParameter("id", id)
        .setParameter("sku", r.sku())
        .setParameter("qty", r.quantity())
        .setParameter("amount", product.price().multiply(BigDecimal.valueOf(r.quantity())))
        .setParameter("currency", product.currency())
        .setParameter("note", r.note())
        .setParameter("at", Instant.now())
        .executeUpdate();
    return view(require(id));
  }

  @Transactional(readOnly = true)
  public OrderResponse get(UUID id) {
    return view(require(id));
  }

  private PurchaseOrder require(UUID id) {
    return em.createQuery("select o from PurchaseOrder o where o.id=:id", PurchaseOrder.class)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .orElseThrow(
            () -> new ApiException(404, "ORDER_NOT_FOUND", "No order exists with this ID."));
  }

  @Transactional(readOnly = true)
  public OrderPage page(String status, int page, int size, String sort) {
    if (page < 0
        || page > 100000
        || size < 1
        || size > 100
        || !Set.of("createdAt", "amount").contains(sort)
        || (status != null && !Set.of("DRAFT", "PENDING", "PAID").contains(status)))
      throw new ApiException(
          400,
          "INVALID_QUERY",
          "Use page>=0, size=1..100, sort=createdAt|amount, status=DRAFT|PENDING|PAID.");
    String where = status == null ? "" : " where o.status=:status";
    var query =
        em.createQuery(
            "select o from PurchaseOrder o" + where + " order by o." + sort + " desc,o.id",
            PurchaseOrder.class);
    var count = em.createQuery("select count(o) from PurchaseOrder o" + where, Long.class);
    if (status != null) {
      query.setParameter("status", status);
      count.setParameter("status", status);
    }
    return new OrderPage(
        query.setFirstResult(page * size).setMaxResults(size).getResultList().stream()
            .map(this::view)
            .toList(),
        page,
        size,
        count.getSingleResult());
  }

  @Transactional
  public OrderResponse replace(UUID id, OrderRequest r, ProductResponse product) {
    int count =
        em.unwrap(Session.class)
            .createMutationQuery(
                """
update PurchaseOrder o set o.sku=:sku,o.quantity=:qty,o.amount=:amount,o.currency=:currency,o.note=:note
where o.id=:id and o.status='DRAFT'
""")
            .setParameter("id", id)
            .setParameter("sku", r.sku())
            .setParameter("qty", r.quantity())
            .setParameter("amount", product.price().multiply(BigDecimal.valueOf(r.quantity())))
            .setParameter("currency", product.currency())
            .setParameter("note", r.note())
            .executeUpdate();
    if (count == 0) notEditable(id);
    em.clear();
    return view(require(id));
  }

  @Transactional
  public OrderResponse note(UUID id, String note) {
    int count =
        em.unwrap(Session.class)
            .createMutationQuery(
                "update PurchaseOrder o set o.note=:note where o.id=:id and o.status='DRAFT'")
            .setParameter("note", note)
            .setParameter("id", id)
            .executeUpdate();
    if (count == 0) notEditable(id);
    em.clear();
    return view(require(id));
  }

  @Transactional
  public void delete(UUID id) {
    int count =
        em.unwrap(Session.class)
            .createMutationQuery("delete from PurchaseOrder o where o.id=:id and o.status='DRAFT'")
            .setParameter("id", id)
            .executeUpdate();
    if (count == 0) notEditable(id);
  }

  private void notEditable(UUID id) {
    require(id);
    throw new ApiException(409, "ORDER_LOCKED", "Only a DRAFT order can be edited or deleted.");
  }

  @Transactional
  public OrderResponse beginCheckout(UUID id) {
    // Freeze the payable payload before any network call; retries always send the same
    // amount/quantity.
    em.unwrap(Session.class)
        .createMutationQuery(
            "update PurchaseOrder o set o.status='PENDING' where o.id=:id and o.status='DRAFT'")
        .setParameter("id", id)
        .executeUpdate();
    em.clear();
    return view(require(id));
  }

  @Transactional
  public void markPaid(PaymentCaptured e) {
    e.validate();
    if (em.createQuery("select count(p) from ProcessedEvent p where p.eventId=:id", Long.class)
            .setParameter("id", e.eventId())
            .getSingleResult()
        > 0) return;
    int fresh =
        em.unwrap(Session.class)
            .createMutationQuery(
                "insert into ProcessedEvent(eventId,processedAt) values (:id,:at) on conflict"
                    + " (eventId) do nothing")
            .setParameter("id", e.eventId())
            .setParameter("at", Instant.now())
            .executeUpdate();
    if (fresh == 0) return;
    PurchaseOrder order = require(e.orderId());
    if (!order.getSku().equals(e.sku())
        || order.getQuantity() != e.quantity()
        || order.getAmount().compareTo(e.amount()) != 0
        || !order.getCurrency().equals(e.currency())
        || "DRAFT".equals(order.getStatus())
        || (order.getPaymentId() != null && !order.getPaymentId().equals(e.paymentId())))
      throw new IllegalArgumentException("Payment event does not match the frozen order");
    em.unwrap(Session.class)
        .createMutationQuery(
            "update PurchaseOrder o set o.status='PAID',o.paymentId=:payment where o.id=:id")
        .setParameter("payment", e.paymentId())
        .setParameter("id", e.orderId())
        .executeUpdate();
    em.clear();
  }

  private OrderResponse view(PurchaseOrder o) {
    return new OrderResponse(
        o.getId(),
        o.getSku(),
        o.getQuantity(),
        o.getAmount(),
        o.getCurrency(),
        o.getNote(),
        o.getStatus(),
        o.getPaymentId(),
        o.getCreatedAt());
  }
}
