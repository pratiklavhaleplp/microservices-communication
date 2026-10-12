package dev.pratik.lab.inventory;

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
@Import(InventoryStore.class)
class InventoryStoreTest {
  @Autowired InventoryStore store;

  @Test
  void duplicateDeliveryCountsSaleExactlyOnce() {
    var e =
        new PaymentCaptured(
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BOOK-1",
            2,
            new BigDecimal("1000.00"),
            "INR",
            "test",
            Instant.now());
    assertThat(store.recordSale(e)).isTrue();
    assertThat(store.recordSale(e)).isFalse();
    assertThat(store.sales("BOOK-1"))
        .containsEntry("soldUnits", 2L)
        .containsEntry("receiptCount", 1L);
  }

  @Test
  void unknownSkuIs404() {
    assertThatThrownBy(() -> store.product("MISSING")).isInstanceOf(ApiException.class);
  }
}
