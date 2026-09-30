package local.builderday.common.exception;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * MVC adapter of ADR 0001. Spring's base handler maps framework exceptions (400, 404, 405, 415, ...) to a status;
 * this replaces every body with the fixed vocabulary entry for that status, so no exception message reaches a client.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private static final ApiError[] FRAMEWORK_ERRORS = {
      ApiError.INVALID_REQUEST, ApiError.RESOURCE_NOT_FOUND, ApiError.METHOD_NOT_ALLOWED,
      ApiError.UNSUPPORTED_MEDIA_TYPE, ApiError.INTERNAL_ERROR};

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) throws Exception {
    // Spring Security translates its own exceptions (ExceptionTranslationFilter), never MVC advice.
    if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) throw exception;
    log.error("Unhandled request failure", exception);
    return ResponseEntity.internalServerError().body(ProblemDetails.of(ApiError.INTERNAL_ERROR));
  }

  /**
   * A whole-row save lost a race with another write (ADR 0013). Mapped once here so every admin writer fails fast with
   * the same answer; no Security audit event (a technical race, not a refused attempt), and the log names only the
   * entity id, never the exception message or any Account data.
   */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> handleConcurrentModification(ObjectOptimisticLockingFailureException exception) {
    log.atInfo().addKeyValue("entity.id", String.valueOf(exception.getIdentifier()))
        .log("Concurrent modification refused");
    return ProblemDetails.response(ApiError.CONCURRENT_MODIFICATION);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
      HttpStatusCode statusCode, WebRequest request) {
    // ponytail: statuses outside the vocabulary (406, 413, 503, ...) get a title-only body; add codes when they occur.
    ProblemDetail problem = Arrays.stream(FRAMEWORK_ERRORS)
        .filter(error -> error.status().value() == statusCode.value()).findFirst()
        .map(ProblemDetails::of)
        .orElseGet(() -> ProblemDetail.forStatus(statusCode));
    if (statusCode.is5xxServerError()) log.error("Request failed", exception);
    return super.handleExceptionInternal(exception, problem, headers, statusCode, request);
  }
}
