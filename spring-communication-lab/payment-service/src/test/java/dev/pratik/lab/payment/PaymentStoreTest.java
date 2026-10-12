package dev.pratik.lab.payment;

import static dev.pratik.lab.payment.PaymentDtos.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pratik.lab.common.ApiException;
import io.micrometer.tracing.Tracer;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "eureka.client.enabled=false"
    })
@Import({PaymentStore.class, PaymentStoreTest.JsonConfig.class})
class PaymentStoreTest {
  @Autowired PaymentStore store;
  @jakarta.persistence.PersistenceContext EntityManager em;
  @MockitoBean Tracer tracer;

  @org.springframework.boot.test.context.TestConfiguration
  static class JsonConfig {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }
  }

  ChargeRequest request(UUID order, String amount) {
    return new ChargeRequest(order, "BOOK-1", 1, new BigDecimal(amount), "INR");
  }

  @Test
  void sameKeyAndPayloadReturnsOriginalPaymentAndOnlyOneOutboxRow() {
    long before =
        em.createQuery("select count(e) from OutboxEvent e", Long.class).getSingleResult();
    UUID order = UUID.randomUUID();
    String key = UUID.randomUUID().toString();
    var first = store.charge(key, request(order, "500.00"), "test");
    var second = store.charge(key, request(order, "500.0"), "test");
    assertThat(first.replayed()).isFalse();
    assertThat(second.replayed()).isTrue();
    assertThat(first.payment().id()).isEqualTo(second.payment().id());
    assertThat(store.paymentCount(order)).isEqualTo(1);
    assertThat(em.createQuery("select count(e) from OutboxEvent e", Long.class).getSingleResult())
        .isEqualTo(before + 1);
  }

  @Test
  void sameKeyWithDifferentPayloadIsConflict() {
    UUID order = UUID.randomUUID();
    String key = UUID.randomUUID().toString();
    store.charge(key, request(order, "500.00"), "test");
    assertThatThrownBy(() -> store.charge(key, request(order, "501.00"), "test"))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).status())
        .isEqualTo(409);
  }

  @Test
  void missingPaymentIs404() {
    assertThatThrownBy(() -> store.byOrder(UUID.randomUUID()))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).status())
        .isEqualTo(404);
  }
}
