package com.example.hello.hello;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Story 5: the protected greeting. */
@RestController
public class HelloController {

  @GetMapping(path = "/api/hello", produces = MediaType.APPLICATION_JSON_VALUE)
  public HelloResponse hello(Authentication authentication) {
    return new HelloResponse("Hello, " + authentication.getName());
  }
}
