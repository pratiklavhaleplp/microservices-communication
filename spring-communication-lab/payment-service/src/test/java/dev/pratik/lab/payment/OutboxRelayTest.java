package dev.pratik.lab.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pratik.lab.common.KafkaConfiguration;
import io.micrometer.tracing.*;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "eureka.client.enabled=false"
    })
@Import({PaymentStore.class, OutboxRelay.class, OutboxRelayTest.Config.class})
class OutboxRelayTest {
  @Autowired PaymentStore store;
  @Autowired OutboxRelay relay;
  @jakarta.persistence.PersistenceContext EntityManager em;
  @MockitoBean KafkaTemplate<String, String> kafka;
  @MockitoBean Tracer tracer;
  @MockitoBean Propagator propagator;

  @TestConfiguration
  static class Config {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
      return new TransactionTemplate(manager);
    }
  }

  @BeforeEach
  void tracing() {
    Span.Builder builder = mock(Span.Builder.class, RETURNS_SELF);
    when(propagator.extract(any(), any())).thenReturn(builder);
    when(builder.start()).thenReturn(mock(Span.class));
  }

  @Test
  void failedKafkaPublishKeepsOutboxPendingAndLaterAcknowledgementMarksItSent() {
    UUID order = UUID.randomUUID();
    store.charge(
        "test-" + order,
        new PaymentDtos.ChargeRequest(order, "BOOK-1", 1, new BigDecimal("500"), "INR"),
        "outbox-test");
    when(kafka.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.failedFuture(new IllegalStateException("Broker unavailable")));
    relay.publishPending();
    em.clear();
    OutboxEvent failed =
        em.createQuery("select e from OutboxEvent e", OutboxEvent.class).getSingleResult();
    assertThat(failed.getSent()).isFalse();
    assertThat(failed.getAttempts()).isEqualTo(1);

    when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
    relay.publishPending();
    em.clear();
    OutboxEvent sent =
        em.createQuery("select e from OutboxEvent e", OutboxEvent.class).getSingleResult();
    assertThat(sent.getSent()).isTrue();
    assertThat(sent.getAttempts()).isEqualTo(2);
    ArgumentCaptor<ProducerRecord<String, String>> records =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafka, times(2)).send(records.capture());
    assertThat(records.getAllValues())
        .allMatch(
            r ->
                r.topic().equals(KafkaConfiguration.PAYMENTS)
                    && r.key().equals(order.toString())
                    && r.value().contains(sent.getId().toString()));
    assertThat(records.getAllValues().get(0).value())
        .isEqualTo(records.getAllValues().get(1).value());
  }
}
