package dev.pratik.lab.order;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "payment-service")
public interface PaymentFeignClient {
  // No fixed URL: Spring Cloud LoadBalancer asks SimpleDiscoveryClient or Eureka for instances.
  @GetMapping("/api/v1/payments/instance")
  Map<String, String> instance(
      @RequestHeader("Authorization") String authorization,
      @RequestHeader("X-Correlation-ID") String correlation);
}
