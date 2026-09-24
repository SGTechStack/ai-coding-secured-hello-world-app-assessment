package com.example.demo_app.security;

import com.example.demo_app.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.stereotype.Component;

/**
 * Renders security failures as JSON {@link ApiError} bodies so the API never redirects or serves
 * HTML: {@code 401} when authentication is missing or fails, {@code 403} when access is denied
 * (including a missing or invalid CSRF token). A session that was expired (see {@link
 * SessionExpiry}) gets the same {@code 401} on its next request, not Spring Security's default
 * plain-text message.
 */
@Component
class JsonSecurityErrorHandler
    implements AuthenticationEntryPoint, AccessDeniedHandler, SessionInformationExpiredStrategy {

  private final ObjectMapper objectMapper;

  JsonSecurityErrorHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden");
  }

  @Override
  public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
    write(
        event.getRequest(),
        event.getResponse(),
        HttpStatus.UNAUTHORIZED,
        "UNAUTHORIZED",
        "Unauthorized");
  }

  private void write(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(), ApiError.of(status, code, message, request));
  }
}
