package dev.pratik.lab.inventory;

import dev.pratik.lab.common.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class InventoryStore {
  @PersistenceContext private EntityManager em;

  public record ProductView(String sku, String name, BigDecimal price, String currency) {}

  @Transactional(readOnly = true)
  public ProductView product(String sku) {
    return em.createQuery(
            "select new"
                + " dev.pratik.lab.inventory.InventoryStore$ProductView(p.sku,p.name,p.price,p.currency)"
                + " from Product p where p.sku=:sku",
            ProductView.class)
        .setParameter("sku", sku)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new ApiException(404, "PRODUCT_NOT_FOUND", "Unknown SKU."));
  }

  @Transactional
  public boolean recordSale(PaymentCaptured event) {
    event.validate();
    // Fast path for ordinary redelivery; the unique constraint below still handles races.
    if (em.createQuery("select count(s) from SalesReceipt s where s.eventId=:id", Long.class)
            .setParameter("id", event.eventId())
            .getSingleResult()
        > 0) return false;
    // Unique event ID prevents repeat delivery; order/payment uniqueness also rejects a conflicting
    // event ID.
    return em.unwrap(Session.class)
            .createMutationQuery(
                """
                insert into SalesReceipt(eventId,orderId,paymentId,sku,quantity,receivedAt)
                values (:event,:order,:payment,:sku,:quantity,:at) on conflict (eventId) do nothing
                """)
            .setParameter("event", event.eventId())
            .setParameter("order", event.orderId())
            .setParameter("payment", event.paymentId())
            .setParameter("sku", event.sku())
            .setParameter("quantity", event.quantity())
            .setParameter("at", Instant.now())
            .executeUpdate()
        == 1;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> sales(String sku) {
    Long units =
        em.createQuery(
                "select coalesce(sum(s.quantity),0) from SalesReceipt s where s.sku=:sku",
                Long.class)
            .setParameter("sku", sku)
            .getSingleResult();
    Long receipts =
        em.createQuery("select count(s) from SalesReceipt s where s.sku=:sku", Long.class)
            .setParameter("sku", sku)
            .getSingleResult();
    return Map.of("sku", sku, "soldUnits", units, "receiptCount", receipts);
  }
}
