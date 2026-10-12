package dev.pratik.lab.order;

import dev.pratik.lab.common.ApiException;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ServiceTokens {
  private final OAuth2AuthorizedClientManager manager;

  public ServiceTokens(OAuth2AuthorizedClientManager manager) {
    this.manager = manager;
  }

  public String accessToken() {
    try {
      var request =
          OAuth2AuthorizeRequest.withClientRegistrationId("service")
              .principal("order-service")
              .build();
      var client = manager.authorize(request);
      if (client == null) throw new IllegalStateException("No OAuth client");
      return client.getAccessToken().getTokenValue();
    } catch (Exception e) {
      throw new ApiException(503, "TOKEN_SERVICE_UNAVAILABLE", "Could not obtain a service token.");
    }
  }

  @Configuration
  static class OAuthClientConfig {
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
        ClientRegistrationRepository registrations, OAuth2AuthorizedClientService clients) {
      // Bound token endpoint latency too. The manager caches valid tokens and replaces expired
      // ones.
      var http = new SimpleClientHttpRequestFactory();
      http.setConnectTimeout(1000);
      http.setReadTimeout(2000);
      var rest =
          new RestTemplate(
              List.of(
                  new FormHttpMessageConverter(),
                  new OAuth2AccessTokenResponseHttpMessageConverter()));
      rest.setRequestFactory(http);
      rest.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
      var tokenClient = new DefaultClientCredentialsTokenResponseClient();
      tokenClient.setRestOperations(rest);
      var provider =
          OAuth2AuthorizedClientProviderBuilder.builder()
              .clientCredentials(c -> c.accessTokenResponseClient(tokenClient))
              .build();
      var manager =
          new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
      manager.setAuthorizedClientProvider(provider);
      return manager;
    }
  }
}
