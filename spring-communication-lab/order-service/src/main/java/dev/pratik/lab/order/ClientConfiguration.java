package dev.pratik.lab.order;

import io.netty.channel.ChannelOption;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.*;
import org.springframework.http.client.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class ClientConfiguration {
  @Bean(destroyMethod = "dispose")
  ConnectionProvider paymentPool() {
    return ConnectionProvider.builder("payment-pool")
        .maxConnections(20)
        .pendingAcquireMaxCount(40)
        .pendingAcquireTimeout(Duration.ofMillis(500))
        .build();
  }

  @Bean
  WebClient paymentWebClient(
      WebClient.Builder builder,
      ConnectionProvider paymentPool,
      @Value("${lab.payment.url}") String url,
      @Value("${lab.payment.response-timeout-ms:800}") int responseTimeout) {
    var http =
        reactor.netty.http.client.HttpClient.create(paymentPool)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 500)
            .responseTimeout(Duration.ofMillis(responseTimeout));
    // Use Boot's builder to retain HTTP metrics and trace propagation.
    return builder.baseUrl(url).clientConnector(new ReactorClientHttpConnector(http)).build();
  }

  @Bean
  RestClient inventoryRestClient(
      RestClient.Builder builder, @Value("${lab.inventory.url}") String url) {
    return builder.baseUrl(url).requestFactory(factory()).build();
  }

  @Bean
  RestClient comparisonRestClient(
      RestClient.Builder builder, @Value("${lab.payment.url}") String url) {
    return builder.baseUrl(url).requestFactory(factory()).build();
  }

  @Bean
  RestTemplate comparisonRestTemplate(RestTemplateBuilder builder) {
    return builder.requestFactory(this::factory).build();
  }

  private JdkClientHttpRequestFactory factory() {
    var f =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build());
    f.setReadTimeout(Duration.ofMillis(800));
    return f;
  }
}
