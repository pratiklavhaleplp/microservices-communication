package dev.pratik.lab.order;

import dev.pratik.lab.common.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
@Order(-1)
public class DownstreamAdvice {
  @ExceptionHandler(DownstreamFailure.class)
  ResponseEntity<ApiError> downstream(DownstreamFailure e, HttpServletRequest request) {
    int status =
        e.remoteStatus() == 409 ? 409 : (e.retryable() || e.remoteStatus() == 429 ? 503 : 502);
    var response = ResponseEntity.status(status);
    if (e.retryAfter() != null && e.retryAfter().matches("[0-9]{1,6}"))
      response.header("Retry-After", e.retryAfter());
    return response.body(
        ErrorAdvice.body(
            status,
            "PAYMENT_UNAVAILABLE",
            e.getMessage() + " Retry checkout with the same order ID.",
            request.getRequestURI()));
  }

  @ExceptionHandler(CallNotPermittedException.class)
  ResponseEntity<ApiError> open(CallNotPermittedException e, HttpServletRequest request) {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .body(
            ErrorAdvice.body(
                503,
                "CIRCUIT_OPEN",
                "Payment circuit is open; retry later using the same order ID.",
                request.getRequestURI()));
  }
}
