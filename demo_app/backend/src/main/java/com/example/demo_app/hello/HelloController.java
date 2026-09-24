package com.example.demo_app.hello;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The PRD's protected content. Anonymous requests never reach it: the filter chain requires an
 * authenticated session and answers {@code 401} JSON. The greeting is always JSON, so the username
 * is never rendered as HTML or plain text.
 */
@RestController
class HelloController {

  @GetMapping(value = "/api/v1/hello", produces = MediaType.APPLICATION_JSON_VALUE)
  HelloResponse hello(Authentication authentication) {
    return new HelloResponse("Hello, " + authentication.getName());
  }

  record HelloResponse(String message) {}
}
