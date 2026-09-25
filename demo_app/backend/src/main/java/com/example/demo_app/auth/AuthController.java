package com.example.demo_app.auth;

import com.example.demo_app.user.AccountUserDetails;
import com.example.demo_app.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The session endpoints the SPA calls: the CSRF token, the signed-in profile and login. Login
 * itself is {@link LoginService}; logout is the filter chain's (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

  private final LoginService loginService;

  AuthController(LoginService loginService) {
    this.loginService = loginService;
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
   * Logs in and binds the result to the {@code HttpSession} ({@link LoginService}). A refused login
   * propagates as {@code AuthenticationException} or {@code TooManyRequestsException}, and a blank
   * field is rejected before authentication; the global {@code ApiExceptionHandler} renders them.
   */
  @PostMapping(
      value = "/login",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  UserProfile login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    return UserProfile.of(loginService.logIn(body, request, response));
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
