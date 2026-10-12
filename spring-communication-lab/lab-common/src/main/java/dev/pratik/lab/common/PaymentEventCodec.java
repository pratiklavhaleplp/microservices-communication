package dev.pratik.lab.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Deserialize inside the listener so even malformed JSON can be published unchanged to a DLT. */
@Component
public class PaymentEventCodec {
  private final ObjectMapper json;

  public PaymentEventCodec(ObjectMapper json) {
    this.json = json;
  }

  public PaymentCaptured decode(String payload) {
    try {
      PaymentCaptured event = json.readValue(payload, PaymentCaptured.class);
      if (event == null) throw new IllegalArgumentException("Payment event must be an object");
      event.validate();
      return event;
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Malformed PaymentCaptured JSON", ex);
    }
  }
}
