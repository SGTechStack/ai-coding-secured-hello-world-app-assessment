package com.assessment.auth.security;

import com.assessment.auth.audit.AuditAction;
import com.assessment.auth.audit.AuditEvent;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.audit.AuditReason;
import com.assessment.auth.common.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.event.Level;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The JSON login filter (spec.md S4, filter 4).
 *
 * <p>Registered with {@code addFilterAt(..., UsernamePasswordAuthenticationFilter.class)} — at that
 * position, not before or after it, because it replaces that filter's job rather than supplementing
 * it.
 *
 * <p>Success is <strong>200 with an empty body</strong>; every failure is <strong>401 with an empty
 * body</strong>. Nothing distinguishes an unknown username, a wrong password, a locked account and
 * a disabled account — not the status, not the body, and not, within the floor below, the timing.
 *
 * <p>The audit line for a failure carries <strong>no subject at all</strong>: only {@code trace.id}
 * and {@code source.ip}. If a wrong-password failure named the account while an unknown-username
 * failure did not, the two would be trivially distinguishable in the log even though the HTTP
 * responses matched — a correct response with a leaky log line passes every other test (spec.md
 * S13, test 5).
 */
public class JsonAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

  private static final String START_NANOS = "com.assessment.auth.loginStartNanos";

  private final ObjectMapper objectMapper;
  private final AuditLogger auditLogger;
  private final Duration responseTimeFloor;

  public JsonAuthenticationFilter(
      String loginPath,
      ObjectMapper objectMapper,
      AuditLogger auditLogger,
      Duration responseTimeFloor) {
    super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, loginPath));
    this.objectMapper = objectMapper;
    this.auditLogger = auditLogger;
    this.responseTimeFloor = responseTimeFloor;
    setAuthenticationSuccessHandler(this::onSuccess);
    setAuthenticationFailureHandler(this::onFailure);
  }

  @Override
  public Authentication attemptAuthentication(
      HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
    request.setAttribute(START_NANOS, System.nanoTime());
    String username;
    String password;
    try {
      JsonNode body = objectMapper.readTree(request.getInputStream());
      username = text(body, "username");
      password = text(body, "password");
    } catch (IOException | RuntimeException ex) {
      // A malformed body is an authentication failure, not a 400: distinguishing them would let a
      // caller probe the endpoint's parsing behaviour separately from its credential behaviour.
      throw new AuthenticationServiceException("Malformed authentication request.", ex);
    }
    return getAuthenticationManager()
        .authenticate(new PasswordAuthenticationToken(username, password));
  }

  private static String text(JsonNode body, String field) {
    JsonNode value = body == null ? null : body.get(field);
    return value == null || value.isNull() ? "" : value.asString();
  }

  private void onSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
      auditLogger.emit(
          AuditEvent.of(AuditAction.AUTHENTICATION, AuditReason.LOGIN_SUCCESS, Level.INFO)
              .actor(user.id())
              .sourceIp(request.getRemoteAddr())
              // Set explicitly. AuthN:112-118's resolver switches on the authentication class name
              // and would yield "unknown" for PasswordAuthenticationToken, breaching Std:240.
              .authenticationMethod("password")
              .build());
    }
    enforceResponseTimeFloor(request);
    response.setStatus(HttpStatus.OK.value());
    response.setContentLength(0);
    response.flushBuffer();
  }

  private void onFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
      throws IOException {
    auditLogger.emit(
        AuditEvent.of(AuditAction.AUTHENTICATION, AuditReason.LOGIN_FAILURE, Level.WARN)
            .outcome("failure")
            .sourceIp(request.getRemoteAddr())
            .build());
    enforceResponseTimeFloor(request);
    // Empty body. This is Std:247's most generic response, and it is what HttpStatusEntryPoint
    // produces on the unauthenticated path, so the two are identical.
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentLength(0);
    response.flushBuffer();
  }

  /**
   * Holds the response until the configured floor has elapsed.
   *
   * <p>A <strong>partial</strong> discharge of Std:247's identical-timing clause, deliberately
   * described as such: it bounds the observable difference from below, it does not make the
   * handling constant-time. The test asserts the bound and claims nothing more (spec.md S7).
   */
  private void enforceResponseTimeFloor(HttpServletRequest request) {
    Object start = request.getAttribute(START_NANOS);
    if (!(start instanceof Long startNanos) || responseTimeFloor == null) {
      return;
    }
    long remainingNanos = responseTimeFloor.toNanos() - (System.nanoTime() - startNanos);
    if (remainingNanos <= 0) {
      return;
    }
    try {
      Thread.sleep(Duration.ofNanos(remainingNanos));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
