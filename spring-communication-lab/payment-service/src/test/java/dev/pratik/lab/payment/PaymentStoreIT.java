package dev.pratik.lab.payment;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentStoreIT extends PaymentStoreTest {
  @org.junit.jupiter.api.Test
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void concurrentRetriesUseThePostgresUniqueConstraint() throws Exception {
    var order = java.util.UUID.randomUUID();
    String key = "concurrent-" + order;
    var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
    try {
      var jobs =
          new java.util.ArrayList<
              java.util.concurrent.Callable<dev.pratik.lab.payment.PaymentDtos.ChargeResult>>();
      for (int i = 0; i < 6; i++)
        jobs.add(() -> store.charge(key, request(order, "500.00"), "concurrency-it"));
      var results = pool.invokeAll(jobs);
      var ids = new java.util.HashSet<java.util.UUID>();
      int fresh = 0;
      for (var future : results) {
        var result = future.get();
        ids.add(result.payment().id());
        if (!result.replayed()) fresh++;
      }
      org.assertj.core.api.Assertions.assertThat(ids).hasSize(1);
      org.assertj.core.api.Assertions.assertThat(fresh).isEqualTo(1);
      org.assertj.core.api.Assertions.assertThat(store.paymentCount(order)).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", pg::getJdbcUrl);
    p.add("spring.datasource.username", pg::getUsername);
    p.add("spring.datasource.password", pg::getPassword);
    p.add("spring.flyway.enabled", () -> true);
    p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }
}
