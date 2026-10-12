package dev.pratik.lab.inventory;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryStoreIT extends InventoryStoreTest {
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
