package local.builderday.auth.login.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import local.builderday.common.exception.ApiError;
import local.builderday.common.exception.ProblemDetails;
import local.builderday.auth.login.controller.dto.LoginRequest;
import local.builderday.auth.login.controller.dto.LoginResponse;
import local.builderday.auth.login.service.LoginService;
import local.builderday.auth.login.service.LoginService.Outcome;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class LoginController {
  private final LoginService loginService;

  LoginController(LoginService loginService) {
    this.loginService = loginService;
  }

  @PostMapping("/api/auth/login")
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    return switch (loginService.attempt(request.username(), request.password(), httpRequest, httpResponse)) {
      case Outcome.Authenticated(var profile) -> ResponseEntity.ok(new LoginResponse(profile));
      case Outcome.InvalidCredentials() -> ProblemDetails.response(ApiError.INVALID_CREDENTIALS);
      case Outcome.RateLimited() -> ProblemDetails.response(ApiError.AUTHENTICATION_UNAVAILABLE);
    };
  }
}
