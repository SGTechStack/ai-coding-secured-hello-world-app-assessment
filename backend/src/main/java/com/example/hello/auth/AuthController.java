package com.example.hello.auth;

import com.example.hello.shared.ApiException;
import com.example.hello.user.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final AuthService auth;
  private final LoginThrottle throttle;
  private final UserRepository users;
  private final SecurityContextRepository contexts;
  private final CsrfTokenRepository csrf;

  public AuthController(
      AuthService auth,
      LoginThrottle throttle,
      UserRepository users,
      SecurityContextRepository contexts,
      CsrfTokenRepository csrf) {
    this.auth = auth;
    this.throttle = throttle;
    this.users = users;
    this.contexts = contexts;
    this.csrf = csrf;
  }

  @GetMapping("/auth/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  UserView register(@Valid @RequestBody AuthRequests.Register request) {
    return auth.register(request);
  }

  @PostMapping("/auth/login")
  UserView login(
      @Valid @RequestBody AuthRequests.Login credentials,
      HttpServletRequest request,
      HttpServletResponse response) {
    UserAccount user =
        throttle
            .attempt(request.getRemoteAddr(), () -> auth.authenticate(credentials))
            .orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password."));
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            AuthPrincipal.from(user),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
    new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
    csrf.saveToken(null, request, response);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    request
        .getSession()
        .setAttribute(
            FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, user.getUsername());
    contexts.saveContext(context, request, response);
    return UserView.from(user);
  }

  @GetMapping("/auth/me")
  UserView me(@AuthenticationPrincipal AuthPrincipal principal) {
    return users
        .findById(principal.id())
        .map(UserView::from)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required."));
  }

  @GetMapping(value = "/hello", produces = "application/json")
  org.springframework.http.ResponseEntity<?> hello(
      @AuthenticationPrincipal AuthPrincipal principal) {
    // A JSON string (not an object) is the contract in the PRD.
    return org.springframework.http.ResponseEntity.ok(
        new tools.jackson.databind.node.StringNode("Hello, " + principal.username()));
  }
}
