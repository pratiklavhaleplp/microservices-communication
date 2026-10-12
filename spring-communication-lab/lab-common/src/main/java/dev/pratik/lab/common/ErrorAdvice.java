package dev.pratik.lab.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

@RestControllerAdvice
public class ErrorAdvice {
  private static final Logger log = LoggerFactory.getLogger(ErrorAdvice.class);

  public static ApiError body(int status, String code, String message, String path) {
    return new ApiError(
        Instant.now(),
        status,
        code,
        message,
        path,
        CorrelationFilter.current(),
        MDC.get("traceId"));
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> known(ApiException e, HttpServletRequest r) {
    return response(e.status(), e.code(), e.getMessage(), r);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingRequestHeaderException.class,
    MissingServletRequestParameterException.class
  })
  ResponseEntity<ApiError> invalid(Exception e, HttpServletRequest r) {
    return response(
        400,
        "INVALID_REQUEST",
        "Check JSON, required fields, UUIDs, headers and allowed parameter values.",
        r);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> forbidden(AccessDeniedException e, HttpServletRequest r) {
    return response(403, "FORBIDDEN", "Required scope is missing.", r);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> conflict(DataIntegrityViolationException e, HttpServletRequest r) {
    return response(
        409, "CONFLICT", "A unique value already exists or a database constraint was violated.", r);
  }

  @ExceptionHandler(org.springframework.web.ErrorResponseException.class)
  ResponseEntity<ApiError> framework(
      org.springframework.web.ErrorResponseException e, HttpServletRequest r) {
    return response(e.getStatusCode().value(), "HTTP_ERROR", e.getBody().getTitle(), r);
  }

  // Keep framework errors such as 405/415 at their real status, not a misleading 500.
  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiError> method(Exception e, HttpServletRequest r) {
    return response(405, "METHOD_NOT_ALLOWED", "Unsupported HTTP method.", r);
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiError> media(Exception e, HttpServletRequest r) {
    return response(415, "UNSUPPORTED_MEDIA_TYPE", "Send application/json.", r);
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
  ResponseEntity<ApiError> accept(Exception e, HttpServletRequest r) {
    return response(406, "NOT_ACCEPTABLE", "Request application/json.", r);
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ApiError> missing(Exception e, HttpServletRequest r) {
    return response(404, "NOT_FOUND", "No endpoint exists at this path.", r);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest r) {
    log.error("Unhandled request failure", e);
    return response(
        500,
        "INTERNAL_ERROR",
        "Unexpected server error. Use the correlation ID to inspect logs.",
        r);
  }

  private ResponseEntity<ApiError> response(int s, String c, String m, HttpServletRequest r) {
    var builder = ResponseEntity.status(s);
    if (s == 429) builder.header("Retry-After", "2");
    return builder.body(body(s, c, m, r.getRequestURI()));
  }
}
