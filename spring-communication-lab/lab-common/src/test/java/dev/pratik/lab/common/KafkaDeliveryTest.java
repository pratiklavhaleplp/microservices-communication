package dev.pratik.lab.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;

/** Exercises the real shared configuration against a real in-process KRaft broker. */
@SpringBootTest(
    classes = KafkaDeliveryTest.TestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.application.name=order-service",
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "spring.kafka.consumer.enable-auto-commit=false",
      "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
      "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
      "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
      "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
      "spring.kafka.producer.acks=all",
      "spring.kafka.producer.properties.enable.idempotence=true",
      "spring.kafka.listener.ack-mode=record",
      "logging.level.org.apache.kafka=ERROR",
      "logging.level.kafka=ERROR",
      "logging.level.state.change.logger=ERROR"
    })
@EmbeddedKafka(
    kraft = true,
    partitions = 3,
    brokerProperties = {"auto.create.topics.enable=false", "group.initial.rebalance.delay.ms=0"})
@DirtiesContext
class KafkaDeliveryTest {
  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired ObjectMapper json;
  @Autowired Receivers receivers;
  @Autowired KafkaListenerEndpointRegistry registry;

  @Configuration(proxyBeanMethods = false)
  @Import({KafkaAutoConfiguration.class, KafkaConfiguration.class, PaymentEventCodec.class})
  static class TestApp {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    Receivers receivers(PaymentEventCodec codec) {
      return new Receivers(codec);
    }
  }

  static class Receivers {
    final PaymentEventCodec codec;
    final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    final Map<String, PaymentCaptured> completed = new ConcurrentHashMap<>();
    final List<ConsumerRecord<String, String>> dead = new CopyOnWriteArrayList<>();

    Receivers(PaymentEventCodec codec) {
      this.codec = codec;
    }

    @KafkaListener(
        id = "test-orders",
        topics = KafkaConfiguration.PAYMENTS,
        groupId = KafkaConfiguration.ORDERS_GROUP)
    void orders(String payload) {
      receive("orders", payload);
    }

    @KafkaListener(
        id = "test-inventory",
        topics = KafkaConfiguration.PAYMENTS,
        groupId = KafkaConfiguration.INVENTORY_GROUP)
    void inventory(String payload) {
      receive("inventory", payload);
    }

    @KafkaListener(
        topics = {KafkaConfiguration.ORDERS_DLT, KafkaConfiguration.INVENTORY_DLT},
        groupId = "dlt-test-reader")
    void dead(ConsumerRecord<String, String> record) {
      dead.add(record);
    }

    void receive(String group, String payload) {
      PaymentCaptured event = codec.decode(payload);
      String id = group + event.eventId();
      int attempt = attempts.computeIfAbsent(id, key -> new AtomicInteger()).incrementAndGet();
      if (event.sku().equals("FAIL") || (event.sku().equals("RETRY") && attempt < 3))
        throw new IllegalStateException("Temporary DB failure");
      completed.put(id, event);
    }
  }

  PaymentCaptured event(int version, String sku) {
    return new PaymentCaptured(
        UUID.randomUUID(),
        version,
        UUID.randomUUID(),
        UUID.randomUUID(),
        sku,
        1,
        new BigDecimal("500"),
        "INR",
        "kafka-test",
        Instant.now());
  }

  void publish(PaymentCaptured event) throws Exception {
    kafka
        .send(
            KafkaConfiguration.PAYMENTS, event.orderId().toString(), json.writeValueAsString(event))
        .get(15, TimeUnit.SECONDS);
  }

  void bothCompleted(PaymentCaptured event) {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              assertThat(receivers.completed)
                  .containsKeys("orders" + event.eventId(), "inventory" + event.eventId());
            });
  }

  @Test
  void independentGroupsBothReceiveTheSameEvent() throws Exception {
    PaymentCaptured event = event(1, "BOOK-1");
    publish(event);
    bothCompleted(event);
    assertThat(receivers.completed.get("orders" + event.eventId()).orderId())
        .isEqualTo(event.orderId());
    assertThat(receivers.completed.get("inventory" + event.eventId()).amount())
        .isEqualByComparingTo("500");
  }

  @Test
  void transientConsumerFailureSucceedsOnThirdAttempt() throws Exception {
    PaymentCaptured event = event(1, "RETRY");
    publish(event);
    bothCompleted(event);
    assertThat(receivers.attempts.get("orders" + event.eventId()).get()).isEqualTo(3);
    assertThat(receivers.attempts.get("inventory" + event.eventId()).get()).isEqualTo(3);
    assertThat(receivers.dead).noneMatch(record -> record.key().equals(event.orderId().toString()));
  }

  @Test
  void unsupportedVersionGoesToBothGroupSpecificDeadLetterTopics() throws Exception {
    PaymentCaptured event = event(99, "BOOK-1");
    String payload = json.writeValueAsString(event);
    publish(event);
    assertDeadLetters(event.orderId().toString(), payload);
    assertThat(receivers.completed)
        .doesNotContainKeys("orders" + event.eventId(), "inventory" + event.eventId());
  }

  @Test
  void malformedJsonPreservesRawBytesAndDoesNotBlockLaterRecords() throws Exception {
    String key = UUID.randomUUID().toString();
    String payload = "{not-json";
    ProducerRecord<String, String> record =
        new ProducerRecord<>(KafkaConfiguration.PAYMENTS, key, payload);
    record
        .headers()
        .add("X-Correlation-ID", "bad-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    kafka.send(record).get(15, TimeUnit.SECONDS);
    assertDeadLetters(key, payload);
    receivers.dead.stream()
        .filter(r -> r.key().equals(key))
        .forEach(
            r ->
                assertThat(r.headers().lastHeader("X-Correlation-ID").value())
                    .isEqualTo("bad-json".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    PaymentCaptured good = event(1, "BOOK-1");
    // Same key means the valid record follows the poison record in the same partition.
    kafka
        .send(KafkaConfiguration.PAYMENTS, key, json.writeValueAsString(good))
        .get(15, TimeUnit.SECONDS);
    bothCompleted(good);
  }

  @Test
  void exhaustedTransientFailureGoesToDeadLettersAfterThreeAttempts() throws Exception {
    PaymentCaptured event = event(1, "FAIL");
    publish(event);
    assertDeadLetters(event.orderId().toString(), json.writeValueAsString(event));
    assertThat(receivers.attempts.get("orders" + event.eventId()).get()).isEqualTo(3);
    assertThat(receivers.attempts.get("inventory" + event.eventId()).get()).isEqualTo(3);
  }

  @Test
  void stoppedConsumerCatchesUpWhileOtherGroupContinues() throws Exception {
    var inventory = registry.getListenerContainer("test-inventory");
    inventory.stop();
    PaymentCaptured event = event(1, "BOOK-1");
    try {
      publish(event);
      await()
          .atMost(Duration.ofSeconds(20))
          .untilAsserted(
              () -> assertThat(receivers.completed).containsKey("orders" + event.eventId()));
      assertThat(receivers.completed).doesNotContainKey("inventory" + event.eventId());
    } finally {
      inventory.start();
    }
    ContainerTestUtils.waitForAssignment(inventory, 3);
    bothCompleted(event);
  }

  void assertDeadLetters(String key, String payload) {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              var matching = receivers.dead.stream().filter(r -> key.equals(r.key())).toList();
              assertThat(matching)
                  .extracting(ConsumerRecord::topic)
                  .containsExactlyInAnyOrder(
                      KafkaConfiguration.ORDERS_DLT, KafkaConfiguration.INVENTORY_DLT);
              assertThat(matching).allMatch(r -> payload.equals(r.value()));
            });
  }
}
