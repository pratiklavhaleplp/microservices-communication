package dev.pratik.lab.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.cloud.netflix.eureka.server.EnableEurekaServer
public class DiscoveryApplication {
  public static void main(String[] args) {
    SpringApplication.run(DiscoveryApplication.class, args);
  }
}
