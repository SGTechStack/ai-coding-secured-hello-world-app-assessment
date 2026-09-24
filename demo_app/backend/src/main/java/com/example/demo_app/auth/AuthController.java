package com.example.demo_app.auth;

import com.example.demo_app.user.AccountUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
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

  AuthController(
      AuthenticationManager authenticationManager,
      SessionAuthenticationStrategy sessionAuthenticationStrategy,
      SecurityContextRepository securityContextRepository) {
    this.authenticationManager = authenticationManager;
    this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    this.securityContextRepository = securityContextRepository;
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
   * {@code ApiExceptionHandler} renders both.
   */
  @PostMapping(
      value = "/login",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  UserProfile login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    Authentication authentication =
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));

    sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
    SecurityContext context = contextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    contextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    return UserProfile.of((AccountUserDetails) authentication.getPrincipal());
  }
}
