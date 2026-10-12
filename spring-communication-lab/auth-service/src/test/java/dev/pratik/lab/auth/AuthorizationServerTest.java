package dev.pratik.lab.auth;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthorizationServerTest {
  @Autowired TestRestTemplate http;

  @Test
  void realClientCredentialsGrantIssuesSignedJwt() {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("scope", "orders.read");
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    var r =
        http.withBasicAuth("lab-client", "lab-secret")
            .postForEntity("/oauth2/token", new HttpEntity<>(form, headers), Map.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((String) r.getBody().get("access_token")).contains(".");
    assertThat(r.getBody())
        .containsEntry("token_type", "Bearer")
        .containsEntry("scope", "orders.read");
  }

  @Test
  void wrongClientSecretIsRejected() {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    var r =
        http.withBasicAuth("lab-client", "wrong")
            .postForEntity("/oauth2/token", new HttpEntity<>(form, headers), Map.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
