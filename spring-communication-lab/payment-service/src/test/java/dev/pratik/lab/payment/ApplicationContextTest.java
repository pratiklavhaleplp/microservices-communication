package dev.pratik.lab.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:context_payment;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "spring.kafka.listener.auto-startup=false",
      "spring.kafka.admin.auto-create=false",
      "spring.profiles.active=lab",
      "eureka.client.enabled=false",
      "lab.outbox.delay-ms=60000"
    })
class ApplicationContextTest {
  @MockitoBean JwtDecoder jwtDecoder;

  @Test
  void completeServiceBeanGraphStarts() {}
}
