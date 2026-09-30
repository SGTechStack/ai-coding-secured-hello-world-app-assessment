package org.eds.demo.greeting.api;

import lombok.RequiredArgsConstructor;
import org.eds.demo.greeting.application.GreetingService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hello")
@RequiredArgsConstructor
public class GreetingController {

  private final GreetingService greetingService;

  @GetMapping
  public GreetingResponse hello(Authentication authentication) {
    return GreetingResponse.builder()
        .message(greetingService.greet(authentication.getName()))
        .build();
  }
}
