package dev.pratik.lab.common;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventContractTest {
  @Test
  void unsupportedSchemaGoesToDeadLetterPath() {
    var event =
        new PaymentCaptured(
            UUID.randomUUID(),
            99,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BOOK-1",
            1,
            new BigDecimal("500"),
            "INR",
            "test",
            Instant.now());
    assertThatThrownBy(event::validate).isInstanceOf(IllegalArgumentException.class);
  }
}
