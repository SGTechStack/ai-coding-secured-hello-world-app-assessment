package com.example.demo_app.auth;

import com.example.demo_app.audit.AuditEvent;
import com.example.demo_app.audit.AuditLog;
import com.example.demo_app.security.IpThrottle;
import com.example.demo_app.user.AccountUserDetails;
import com.example.demo_app.web.ApiError;
import com.example.demo_app.web.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

  private final SecurityContextHolderStrategy contextHolder =
      SecurityContextHolder.getContextHolderStrategy();
  private final AuthenticationManager authenticationManager;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final SecurityContextRepository securityContextRepository;
  private final AuditLog auditLog;
  private final IpThrottle loginThrottle;

  AuthController(
      AuthenticationManager authenticationManager,
      SessionAuthenticationStrategy sessionAuthenticationStrategy,
      SecurityContextRepository securityContextRepository,
      AuditLog auditLog,
      @Qualifier("loginThrottle") IpThrottle loginThrottle) {
    this.authenticationManager = authenticationManager;
    this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    this.securityContextRepository = securityContextRepository;
    this.auditLog = auditLog;
    this.loginThrottle = loginThrottle;
  }

  /**
   * Gives the SPA a CSRF token before its first state-changing call: in the body, which a
   * cross-origin SPA can read, and as the {@code XSRF-TOKEN} cookie that the double-submit check
   * compares the header against. The token is raw (unmasked), matching the plain request handler.
   */
  @GetMapping(value = "/csrf", produces = MediaType.APPLICATION_JSON_VALUE)
  CsrfTokenResponse csrf(CsrfToken token) {
    return new CsrfTokenResponse(token.getHeaderName(), token.getToken());
  }

  /**
   * The signed-in user's profile, read from the session principal. Requests without a valid session
   * never reach here: the security entry point answers {@code 401} JSON.
   */
  @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
  UserProfile me(@AuthenticationPrincipal AccountUserDetails user) {
    return UserProfile.of(user);
  }

  /**
   * Authenticates and binds the result to the {@code HttpSession}. Failures propagate as {@code
   * AuthenticationException} and a blank field is rejected before authentication; the global
   * {@code ApiExceptionHandler} renders both. Each attempt that reaches authentication is audited
   * as {@code LOGIN_SUCCESS} or {@code LOGIN_FAILURE}, with the submitted username as the actor.
   *
   * <p>An IP with too many recent failures gets {@code 429} ({@code LOGIN_THROTTLED}) before the
   * credentials are looked at, so even correct ones are refused and no account state changes. Only
   * failures count, so a network of legitimate users isn't throttled by its own successful logins.
   */
  @PostMapping(
      value = "/login",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  UserProfile login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    try {
      loginThrottle.check(request);
    } catch (TooManyRequestsException e) {
      auditLog.record(AuditEvent.LOGIN_THROTTLED, body.username(), request);
      throw e;
    }

    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  body.username(), body.password()));
    } catch (AuthenticationException e) {
      loginThrottle.recordAttempt(request);
      auditLog.record(AuditEvent.LOGIN_FAILURE, body.username(), request);
      throw e;
    }

    sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
    SecurityContext context = contextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    contextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
    auditLog.record(AuditEvent.LOGIN_SUCCESS, authentication.getName(), request);

    return UserProfile.of((AccountUserDetails) authentication.getPrincipal());
  }

  /**
   * A blank or missing login field. The UI never sends one, so this is defence in depth. Login
   * keeps its original single message and no {@code fieldErrors}; other endpoints get the global
   * per-field {@code VALIDATION_FAILED}.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> blankCredentials(HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            ApiError.of(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Username and password are required",
                request));
  }
}
