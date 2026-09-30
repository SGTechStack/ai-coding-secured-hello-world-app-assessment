package local.builderday.account.greeting.controller;

import local.builderday.account.greeting.controller.dto.GreetingResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

  /** The name comes from the Session's principal, which holds the canonical stored username, never from the request. */
  @GetMapping("/api/hello")
  public GreetingResponse hello(@AuthenticationPrincipal UserDetails principal) {
    return new GreetingResponse("Hello, " + principal.getUsername());
  }
}
