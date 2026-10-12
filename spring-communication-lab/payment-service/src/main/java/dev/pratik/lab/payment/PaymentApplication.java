package dev.pratik.lab.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"dev.pratik.lab.payment", "dev.pratik.lab.common"})
@org.springframework.scheduling.annotation.EnableScheduling
public class PaymentApplication {
  public static void main(String[] args) {
    SpringApplication.run(PaymentApplication.class, args);
  }
}
