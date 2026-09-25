package com.example.demo_app.auth;

import com.example.demo_app.audit.Actor;
import com.example.demo_app.audit.AuditEvent;
import com.example.demo_app.audit.AuditLog;
import com.example.demo_app.security.IpThrottle;
import com.example.demo_app.user.AccountUserDetails;
import com.example.demo_app.user.LoginAttempts;
import com.example.demo_app.user.UserAccount;
import com.example.demo_app.web.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * One login attempt, from the IP throttle to the new session, so {@link AuthController} only maps
 * HTTP. Each attempt that reaches authentication is audited as {@code LOGIN_SUCCESS} or {@code
 * LOGIN_FAILURE}, with the submitted username as the actor.
 *
 * <p>An IP with too many recent failures gets {@code 429} ({@code LOGIN_THROTTLED}) before the
 * credentials are looked at, so even correct ones are refused and no account state changes. Only
 * failures count, so a network of legitimate users isn't throttled by its own successful logins.
 *
 * <p>A wrong password counts towards the account's lockout ({@link LoginAttempts}); the failure
 * that sets the lock is audited as {@code ACCOUNT_LOCKED}. A correct password for a locked or
 * disabled account is rejected with the same {@code 401} but doesn't count. A success resets the
 * count before the session is created.
 */
@Component
class LoginService {

  private final SecurityContextHolderStrategy contextHolder =
      SecurityContextHolder.getContextHolderStrategy();
  private final AuthenticationManager authenticationManager;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final SecurityContextRepository securityContextRepository;
  private final AuditLog auditLog;
  private final IpThrottle loginThrottle;
  private final LoginAttempts loginAttempts;

  LoginService(
      AuthenticationManager authenticationManager,
      SessionAuthenticationStrategy sessionAuthenticationStrategy,
      SecurityContextRepository securityContextRepository,
      AuditLog auditLog,
      @Qualifier("loginThrottle") IpThrottle loginThrottle,
      LoginAttempts loginAttempts) {
    this.authenticationManager = authenticationManager;
    this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    this.securityContextRepository = securityContextRepository;
    this.auditLog = auditLog;
    this.loginThrottle = loginThrottle;
    this.loginAttempts = loginAttempts;
  }

  /**
   * Authenticates {@code credentials} and binds the result to a new {@code HttpSession}.
   *
   * @return the signed-in user
   * @throws TooManyRequestsException when the client's IP is throttled
   * @throws AuthenticationException for any refused login (the global handler's generic {@code
   *     401})
   */
  AccountUserDetails logIn(
      LoginRequest credentials, HttpServletRequest request, HttpServletResponse response) {
    throttle(credentials.username(), request);
    Authentication authentication = authenticate(credentials, request);
    loginAttempts.recordSuccess(authentication.getName());
    startSession(authentication, request, response);
    auditLog.record(AuditEvent.LOGIN_SUCCESS, Actor.of(authentication.getName(), request));
    return (AccountUserDetails) authentication.getPrincipal();
  }

  /** Refuses the attempt, audited as {@code LOGIN_THROTTLED}, if the client's IP is throttled. */
  private void throttle(String username, HttpServletRequest request) {
    try {
      loginThrottle.check(request);
    } catch (TooManyRequestsException e) {
      auditLog.record(AuditEvent.LOGIN_THROTTLED, Actor.of(username, request));
      throw e;
    }
  }

  /** Checks the credentials; a refusal counts against the IP and, if wrong, the account. */
  private Authentication authenticate(LoginRequest credentials, HttpServletRequest request) {
    String username = credentials.username();
    try {
      return authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(username, credentials.password()));
    } catch (AuthenticationException e) {
      loginThrottle.recordAttempt(request);
      auditLog.record(AuditEvent.LOGIN_FAILURE, Actor.of(username, request));
      if (e instanceof BadCredentialsException && loginAttempts.recordFailure(username)) {
        auditLog.record(
            AuditEvent.ACCOUNT_LOCKED, Actor.of(UserAccount.normaliseUsername(username), request));
      }
      throw e;
    }
  }

  /**
   * Binds {@code authentication} to the session: a new session ID (fixation protection), a
   * rotated CSRF token and a registered session, then the saved security context.
   */
  private void startSession(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
    SecurityContext context = contextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    contextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
  }
}
