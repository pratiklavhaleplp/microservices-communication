package dev.pratik.lab.inventory;

import dev.pratik.lab.common.*;
import org.slf4j.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {
  private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);
  private final InventoryStore store;
  private final PaymentEventCodec codec;

  public PaymentEventListener(InventoryStore store, PaymentEventCodec codec) {
    this.store = store;
    this.codec = codec;
  }

  @KafkaListener(topics = KafkaConfiguration.PAYMENTS, groupId = KafkaConfiguration.INVENTORY_GROUP)
  public void receive(String payload) {
    PaymentCaptured event = codec.decode(payload);
    try (var ignored = MDC.putCloseable("correlationId", event.correlationId())) {
      store.recordSale(event);
      log.info("Applied payment event {}, order={}", event.eventId(), event.orderId());
    }
    // The store's DB transaction commits before return; RECORD mode then commits the offset.
    // If we crash between those commits, the store's event-ID constraint makes replay harmless.
  }
}
