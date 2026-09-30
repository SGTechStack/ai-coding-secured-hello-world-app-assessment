package com.example.hello.auth;

import com.example.hello.common.AuditLogger;
import com.example.hello.common.InvalidCredentialsException;
import com.example.hello.common.TooManyLoginAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/**
 * Story 2: JSON login. Delegates credential checking to Spring Security's
 * {@link AuthenticationManager}, then persists the result in the server-side session exactly
 * the way the built-in form-login filter would (session id rotation, CSRF rotation, context
 * save). Every failure mode collapses into one generic 401.
 */
@Service
public class LoginService {

  private final AuthenticationManager authenticationManager;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final SecurityContextRepository securityContextRepository;
  private final AccountLockoutService lockoutService;
  private final IpLoginThrottle ipThrottle;
  private final AuditLogger audit;
  private final SecurityContextHolderStrategy contextHolderStrategy =
      SecurityContextHolder.getContextHolderStrategy();

  public LoginService(
      AuthenticationManager authenticationManager,
      SessionAuthenticationStrategy sessionAuthenticationStrategy,
      SecurityContextRepository securityContextRepository,
      AccountLockoutService lockoutService,
      IpLoginThrottle ipThrottle,
      AuditLogger audit) {
    this.authenticationManager = authenticationManager;
    this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    this.securityContextRepository = securityContextRepository;
    this.lockoutService = lockoutService;
    this.ipThrottle = ipThrottle;
    this.audit = audit;
  }

  public SessionUser login(LoginRequest credentials, HttpServletRequest request, HttpServletResponse response) {
    String username = credentials.username().trim();
    String ip = request.getRemoteAddr();

    if (ipThrottle.isThrottled(ip)) {
      audit.event("LOGIN_THROTTLED", "ip", ip);
      throw new TooManyLoginAttemptsException();
    }

    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(username, credentials.password()));
    } catch (BadCredentialsException ex) {
      lockoutService.onFailure(username);
      throw reject(username, ip, "bad_credentials");
    } catch (LockedException ex) {
      throw reject(username, ip, "locked");
    } catch (DisabledException ex) {
      throw reject(username, ip, "disabled");
    } catch (AuthenticationException ex) {
      throw reject(username, ip, ex.getClass().getSimpleName());
    }

    lockoutService.onSuccess(username);
    sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

    SecurityContext context = contextHolderStrategy.createEmptyContext();
    context.setAuthentication(authentication);
    contextHolderStrategy.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    audit.event("LOGIN_SUCCESS", "username", username, "ip", ip);
    return SessionUser.from(authentication);
  }

  private InvalidCredentialsException reject(String username, String ip, String reason) {
    ipThrottle.recordFailure(ip);
    audit.event("LOGIN_FAILURE", "username", username, "ip", ip, "reason", reason);
    return new InvalidCredentialsException();
  }
}
