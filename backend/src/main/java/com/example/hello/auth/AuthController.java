package com.example.hello.auth;

import com.example.hello.user.RegisterRequest;
import com.example.hello.user.RegistrationService;
import com.example.hello.user.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, login, session introspection and the CSRF handshake. Logout is handled by
 * Spring Security's logout filter at {@code POST /api/auth/logout}.
 */
@RestController
@RequestMapping(path = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

  private final RegistrationService registrationService;
  private final LoginService loginService;

  public AuthController(RegistrationService registrationService, LoginService loginService) {
    this.registrationService = registrationService;
    this.loginService = loginService;
  }

  @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public UserSummary register(@Valid @RequestBody RegisterRequest request) {
    return registrationService.register(request);
  }

  @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  public SessionUser login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    return loginService.login(request, servletRequest, servletResponse);
  }

  @GetMapping("/me")
  public SessionUser me(Authentication authentication) {
    if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
      return SessionUser.anonymous();
    }
    return SessionUser.from(authentication);
  }

  @GetMapping("/csrf")
  public CsrfTokenResponse csrf(CsrfToken csrfToken) {
    return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
  }
}
