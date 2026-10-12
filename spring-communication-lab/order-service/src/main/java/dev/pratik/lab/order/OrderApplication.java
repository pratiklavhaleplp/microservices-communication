package dev.pratik.lab.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"dev.pratik.lab.order", "dev.pratik.lab.common"})
@org.springframework.cloud.openfeign.EnableFeignClients
public class OrderApplication {
  public static void main(String[] args) {
    SpringApplication.run(OrderApplication.class, args);
  }
}
