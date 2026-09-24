package com.example.demo_app.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders every exception that reaches a controller as an {@link ApiError} body. Spring MVC's
 * own exceptions (malformed JSON, unsupported media type, unknown path, wrong method, ...) keep
 * their status via {@link ResponseEntityExceptionHandler} but get our JSON body instead of a
 * problem detail; anything unexpected is a generic {@code 500}. Security failures raised before
 * a controller runs are rendered by the security filter chain's JSON handler, and errors raised
 * outside Spring MVC by {@code ApiErrorController}, all with the same shape.
 *
 * <p>Responses always declare {@code application/json}, so the body is JSON whatever the
 * client's {@code Accept} header asks for.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /**
   * Every authentication failure (unknown user, wrong password, disabled account) gets the same
   * status and body so the response cannot be used to enumerate usernames; {@code
   * DaoAuthenticationProvider} already equalises timing for unknown users.
   */
  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ApiError> invalidCredentials(HttpServletRequest request) {
    return json(
        ApiError.of(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            "Invalid username or password",
            request));
  }

  /**
   * A throttled client. {@code Retry-After} says when to try again; the SPA can read it across
   * origins because the CORS configuration exposes it.
   */
  @ExceptionHandler(TooManyRequestsException.class)
  ResponseEntity<ApiError> tooManyRequests(
      TooManyRequestsException ex, HttpServletRequest request) {
    ApiError body =
        ApiError.of(
            HttpStatus.TOO_MANY_REQUESTS,
            "TOO_MANY_REQUESTS",
            "Too many attempts. Please try again later.",
            request);
    return ResponseEntity.status(body.status())
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfter().toSeconds()))
        .body(body);
  }

  /**
   * Left to Spring Security, which answers {@code 401} or {@code 403} depending on whether the
   * caller is authenticated. Rethrowing from a handler makes Spring MVC propagate the original
   * exception, so the catch-all below never turns an access denial into a {@code 500}.
   */
  @ExceptionHandler(AccessDeniedException.class)
  void accessDenied(AccessDeniedException ex) {
    throw ex;
  }

  /** Anything unexpected: logged here in full, answered with a generic body. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
    return json(ApiError.forStatus(HttpStatus.INTERNAL_SERVER_ERROR, request.getRequestURI()));
  }

  /** A blank or missing login field. The UI never sends one, so this is defence in depth. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ApiError body =
        ApiError.of(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED",
            "Username and password are required",
            servletRequest(request));
    return handleExceptionInternal(ex, body, headers, status, request);
  }

  /** Unreadable JSON, or a body cut off by {@link RequestBodyLimitFilter}. */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof RequestBodyLimitFilter.BodyTooLargeException) {
        ApiError body = RequestBodyLimitFilter.tooLarge(servletRequest(request).getRequestURI());
        return handleExceptionInternal(
            ex, body, headers, HttpStatusCode.valueOf(body.status()), request);
      }
    }
    return super.handleHttpMessageNotReadable(ex, headers, status, request);
  }

  /** Every Spring MVC exception ends here: swap the problem detail for an {@link ApiError}. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String path = servletRequest(request).getRequestURI();
    if (status.is5xxServerError()) {
      log.error("Request to {} failed with {}", path, status.value(), ex);
    }
    ApiError apiError = body instanceof ApiError given ? given : ApiError.forStatus(status, path);
    HttpHeaders jsonHeaders = new HttpHeaders();
    jsonHeaders.addAll(headers);
    jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
    return super.handleExceptionInternal(
        ex, apiError, jsonHeaders, HttpStatusCode.valueOf(apiError.status()), request);
  }

  private static ResponseEntity<ApiError> json(ApiError body) {
    return ResponseEntity.status(body.status()).contentType(MediaType.APPLICATION_JSON).body(body);
  }

  private static HttpServletRequest servletRequest(WebRequest request) {
    return ((NativeWebRequest) request).getNativeRequest(HttpServletRequest.class);
  }
}
