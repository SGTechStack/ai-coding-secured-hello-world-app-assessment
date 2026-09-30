package org.eds.demo.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.eds.demo.auth.application.SignInService;
import org.eds.demo.auth.application.SignInThrottledException;
import org.eds.demo.user.application.PasswordChangeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON sign-in. Lives outside {@code /api/**} at {@code /login} so it stays on the application
 * chain, where CSRF is enforced and the path is public. Sign-out is Spring Security's logout filter
 * on the same chain.
 */
@RestController
@RequiredArgsConstructor
public class SignInController {

  static final String SIGN_IN_PATH = "/login";

  /** One message for every refusal so the response never reveals why sign-in failed. */
  private static final String GENERIC_FAILURE_DETAIL = SignInService.INVALID_CREDENTIALS_MESSAGE;

  private static final String THROTTLED_DETAIL =
      "Too many failed sign-in attempts. Try again later.";

  /** Issues a fresh session id at sign-in so a pre-planted id cannot be reused. */
  private final SessionAuthenticationStrategy sessionFixationStrategy =
      new ChangeSessionIdAuthenticationStrategy();

  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  private final SignInService signInService;
  private final PasswordChangeService passwordChangeService;

  @PostMapping(SIGN_IN_PATH)
  public SignInResponse signIn(
      @RequestBody SignInRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    var authentication =
        signInService.signIn(request.username(), request.password(), httpRequest.getRemoteAddr());
    sessionFixationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, httpRequest, httpResponse);
    return SignInResponse.builder()
        .username(authentication.getName())
        .mustChangePassword(passwordChangeService.isChangeRequired(authentication.getName()))
        .build();
  }

  /**
   * The address, not an Account, is throttled, so this can be shown without revealing whether a
   * username exists.
   */
  @ExceptionHandler(SignInThrottledException.class)
  ResponseEntity<ProblemDetail> handleThrottled(SignInThrottledException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, THROTTLED_DETAIL);
    problem.setTitle("Sign-in throttled");
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds(e)))
        .body(problem);
  }

  private static long retryAfterSeconds(SignInThrottledException e) {
    return Math.max(1, e.getRetryAfter().plusMillis(999).toSeconds());
  }

  @ExceptionHandler(BadCredentialsException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  ProblemDetail handleBadCredentials() {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, GENERIC_FAILURE_DETAIL);
    problem.setTitle("Sign-in failed");
    return problem;
  }
}
