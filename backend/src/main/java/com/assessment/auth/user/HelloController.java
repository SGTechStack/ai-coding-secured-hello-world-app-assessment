package com.assessment.auth.user;

import com.assessment.auth.common.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The protected greeting (story 1.7).
 *
 * <p>Matrix row 20 is the <strong>only</strong> {@code hasRole('USER')} row in the application,
 * which is what keeps the role hierarchy {@code ROLE_USER_MANAGER > ROLE_USER} load-bearing: a
 * USER_MANAGER reaches this endpoint through the hierarchy, with no explicit grant.
 *
 * <p>An unauthenticated request answers <strong>401, not 403</strong> — that depends on the
 * explicit {@code HttpStatusEntryPoint}, because {@code createDefaultEntryPoint} would otherwise
 * return {@code Http403ForbiddenEntryPoint} (spec.md S13, test 4).
 */
@RestController
public class HelloController {

  public record Greeting(String message) {}

  @GetMapping("${api.base-path}/hello")
  public Greeting hello(@AuthenticationPrincipal AuthenticatedUser user) {
    return new Greeting("Hello, " + user.username() + "!");
  }
}
