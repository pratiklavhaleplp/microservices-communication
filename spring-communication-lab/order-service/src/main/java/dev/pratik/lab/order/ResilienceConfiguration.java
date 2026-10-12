package dev.pratik.lab.order;

import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.*;
import java.time.Duration;
import org.springframework.context.annotation.*;

@Configuration
public class ResilienceConfiguration {
  @Bean
  Retry paymentRetry(RetryRegistry registry) {
    return registry.retry(
        "payment",
        RetryConfig.custom()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(100, 2.0, 0.25))
            .retryOnException(e -> e instanceof DownstreamFailure f && f.retryable())
            .build());
  }

  @Bean
  CircuitBreaker paymentBreaker(CircuitBreakerRegistry registry) {
    return registry.circuitBreaker(
        "payment",
        CircuitBreakerConfig.custom()
            .slidingWindowSize(4)
            .minimumNumberOfCalls(4)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(5))
            .permittedNumberOfCallsInHalfOpenState(1)
            .ignoreException(e -> e instanceof DownstreamFailure f && !f.retryable())
            .build());
  }
}
