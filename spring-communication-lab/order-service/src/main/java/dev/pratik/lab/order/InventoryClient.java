package dev.pratik.lab.order;

import static dev.pratik.lab.order.OrderDtos.*;

import dev.pratik.lab.common.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class InventoryClient {
  private final RestClient client;
  private final ServiceTokens tokens;

  public InventoryClient(
      @Qualifier("inventoryRestClient") RestClient client, ServiceTokens tokens) {
    this.client = client;
    this.tokens = tokens;
  }

  public ProductResponse product(String sku) {
    try {
      return client
          .get()
          .uri("/api/v1/inventory/{sku}", sku)
          .headers(
              h -> {
                h.setBearerAuth(tokens.accessToken());
                h.set(CorrelationFilter.HEADER, CorrelationFilter.current());
              })
          .retrieve()
          .body(ProductResponse.class);
    } catch (HttpClientErrorException.NotFound e) {
      throw new ApiException(400, "UNKNOWN_SKU", "Choose BOOK-1 or PEN-1.");
    } catch (RestClientException e) {
      throw new ApiException(503, "INVENTORY_UNAVAILABLE", "Inventory could not be reached.");
    }
  }
}
