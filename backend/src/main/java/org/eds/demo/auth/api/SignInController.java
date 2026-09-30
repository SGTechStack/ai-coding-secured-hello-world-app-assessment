package org.eds.demo.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.eds.demo.auth.application.SignInService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  private static final String GENERIC_FAILURE_DETAIL = "Invalid username or password";

  /** Issues a fresh session id at sign-in so a pre-planted id cannot be reused. */
  private final SessionAuthenticationStrategy sessionFixationStrategy =
      new ChangeSessionIdAuthenticationStrategy();

  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  private final SignInService signInService;

  @PostMapping(SIGN_IN_PATH)
  public ResponseEntity<?> signIn(
      @RequestBody SignInRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    try {
      var authentication = signInService.signIn(request.username(), request.password());
      sessionFixationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
      securityContextRepository.saveContext(context, httpRequest, httpResponse);
      return ResponseEntity.ok(SignInResponse.builder().username(authentication.getName()).build());
    } catch (BadCredentialsException e) {
      var problem =
          ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, GENERIC_FAILURE_DETAIL);
      problem.setTitle("Sign-in failed");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }
  }
}
