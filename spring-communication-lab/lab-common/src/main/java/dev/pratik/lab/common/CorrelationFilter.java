package dev.pratik.lab.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationFilter extends OncePerRequestFilter {
  private final org.springframework.beans.factory.ObjectProvider<io.micrometer.tracing.Tracer>
      tracing;

  public CorrelationFilter(
      org.springframework.beans.factory.ObjectProvider<io.micrometer.tracing.Tracer> tracing) {
    this.tracing = tracing;
  }

  public static final String HEADER = "X-Correlation-ID";

  public static String current() {
    String id = MDC.get("correlationId");
    return id == null ? UUID.randomUUID().toString() : id;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = request.getHeader(HEADER);
    // Bound length and characters: an incoming header must not inject log lines.
    if (id == null || !id.matches("[A-Za-z0-9._-]{1,100}")) id = UUID.randomUUID().toString();
    MDC.put("correlationId", id);
    response.setHeader(HEADER, id);
    var tracer = tracing.getIfAvailable();
    if (tracer != null && tracer.currentSpan() != null) {
      tracer.currentSpan().tag("correlation.id", id);
      response.setHeader("X-Trace-ID", tracer.currentSpan().context().traceId());
    }
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove("correlationId");
    }
  }
}
