package dev.pratik.lab.order;

import dev.pratik.lab.common.*;
import org.slf4j.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPaymentListener {
  private static final Logger log = LoggerFactory.getLogger(OrderPaymentListener.class);
  private final OrderStore store;
  private final PaymentEventCodec codec;

  public OrderPaymentListener(OrderStore store, PaymentEventCodec codec) {
    this.store = store;
    this.codec = codec;
  }

  @KafkaListener(topics = KafkaConfiguration.PAYMENTS, groupId = KafkaConfiguration.ORDERS_GROUP)
  public void receive(String payload) {
    PaymentCaptured event = codec.decode(payload);
    try (var ignored = MDC.putCloseable("correlationId", event.correlationId())) {
      store.markPaid(event);
      log.info("Applied payment event {}, order={}", event.eventId(), event.orderId());
    }
    // The store's DB transaction commits before return; RECORD mode then commits the offset.
    // If we crash between those commits, the store's event-ID constraint makes replay harmless.
  }
}
