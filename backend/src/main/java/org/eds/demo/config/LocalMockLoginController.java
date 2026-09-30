package org.eds.demo.config;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.eds.demo.user.domain.Role;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Serves the mock M365 login page and user list for local development only. */
@Hidden
@Controller
@Profile("local")
@RequiredArgsConstructor
public class LocalMockLoginController {

  static final String LOGIN_OPTIONS_URL = "/api/login-options";
  static final String LOGIN_PAGE = "/local-mock-login.html";

  record LoginOption(String username, Set<Role> roles) {}

  private final LocalUsersProperties localUsersProperties;

  @GetMapping("/login")
  public String loginPage() {
    return "forward:" + LOGIN_PAGE;
  }

  @GetMapping(LOGIN_OPTIONS_URL)
  @ResponseBody
  public ResponseEntity<List<LoginOption>> loginOptions() {
    List<LoginOption> options =
        localUsersProperties.users().stream()
            .map(u -> new LoginOption(u.username(), u.roles()))
            .toList();
    return ResponseEntity.ok(options);
  }
}
