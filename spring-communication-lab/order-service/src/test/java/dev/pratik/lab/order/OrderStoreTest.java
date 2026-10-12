package dev.pratik.lab.order;

import static dev.pratik.lab.order.OrderDtos.*;
import static org.assertj.core.api.Assertions.*;

import dev.pratik.lab.common.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "eureka.client.enabled=false"
    })
@Import(OrderStore.class)
class OrderStoreTest {
  @Autowired OrderStore store;
  ProductResponse product = new ProductResponse("BOOK-1", "Book", new BigDecimal("500.00"), "INR");

  OrderResponse create() {
    return store.create(new OrderRequest("BOOK-1", 1, ""), product);
  }

  @Test
  void hqlInsertSelectUpdateDeleteAndPaginationWork() {
    var order = create();
    assertThat(order.amount()).isEqualByComparingTo("500");
    assertThat(store.replace(order.id(), new OrderRequest("BOOK-1", 2, "put"), product).amount())
        .isEqualByComparingTo("1000");
    assertThat(store.note(order.id(), "patched").note()).isEqualTo("patched");
    assertThat(store.page("DRAFT", 0, 10, "amount").total()).isEqualTo(1);
    store.delete(order.id());
    assertThat(store.page(null, 0, 10, "createdAt").total()).isZero();
  }

  @Test
  void frozenOrderCannotChangeAndDuplicateEventIsHarmless() {
    var order = create();
    store.beginCheckout(order.id());
    assertThatThrownBy(() -> store.note(order.id(), "late")).isInstanceOf(ApiException.class);
    var e =
        new PaymentCaptured(
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            order.id(),
            "BOOK-1",
            1,
            new BigDecimal("500.00"),
            "INR",
            "test",
            Instant.now());
    store.markPaid(e);
    store.markPaid(e);
    assertThat(store.get(order.id()).status()).isEqualTo("PAID");
    assertThat(store.get(order.id()).paymentId()).isEqualTo(e.paymentId());
  }

  @Test
  void malformedPaginationAndSortAreRejected() {
    assertThatThrownBy(() -> store.page(null, 0, 0, "amount")).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> store.page(null, 0, 10, "amount desc; delete"))
        .isInstanceOf(ApiException.class);
  }
}
