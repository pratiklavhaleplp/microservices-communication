package dev.pratik.lab.common;

import java.time.Duration;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/** One retained log; independent consumer groups each receive every payment event. */
@Configuration
public class KafkaConfiguration {
  public static final String PAYMENTS = "payment.captured.v1";
  public static final String ORDERS_GROUP = "order-service";
  public static final String INVENTORY_GROUP = "inventory-service";
  public static final String ORDERS_DLT = PAYMENTS + "." + ORDERS_GROUP + ".DLT";
  public static final String INVENTORY_DLT = PAYMENTS + "." + INVENTORY_GROUP + ".DLT";

  @Bean
  KafkaAdmin.NewTopics paymentTopics() {
    // Replication factor 1 fits this one-broker lab; it provides no broker redundancy.
    return new KafkaAdmin.NewTopics(
        TopicBuilder.name(PAYMENTS)
            .partitions(3)
            .replicas(1)
            .config("retention.ms", "604800000")
            .build(),
        TopicBuilder.name(ORDERS_DLT).partitions(3).replicas(1).build(),
        TopicBuilder.name(INVENTORY_DLT).partitions(3).replicas(1).build());
  }

  @Bean
  DefaultErrorHandler paymentEventErrorHandler(
      KafkaTemplate<String, String> template,
      @Value("${spring.application.name}") String applicationName) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            template,
            (record, exception) -> {
              String group =
                  exception instanceof ListenerExecutionFailedException failure
                          && failure.getGroupId() != null
                      ? failure.getGroupId()
                      : applicationName;
              String topic =
                  switch (group) {
                    case ORDERS_GROUP -> ORDERS_DLT;
                    case INVENTORY_GROUP -> INVENTORY_DLT;
                    default ->
                        throw new IllegalStateException("Unknown payment consumer group: " + group);
                  };
              return new TopicPartition(topic, record.partition());
            });
    // Do not advance the source offset if publishing the failed record to the DLT failed.
    recoverer.setFailIfSendResultIsError(true);
    recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(12));
    var backoff = new ExponentialBackOffWithMaxRetries(2); // Original + 2 retries = 3 attempts.
    backoff.setInitialInterval(200);
    backoff.setMultiplier(2);
    backoff.setMaxInterval(1000);
    var handler = new DefaultErrorHandler(recoverer, backoff);
    // Malformed JSON and unsupported schema cannot recover by retrying unchanged bytes.
    handler.addNotRetryableExceptions(IllegalArgumentException.class);
    return handler;
  }
}
