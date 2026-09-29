package com.example.hello.auth;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ThrottleErrors {
  @ExceptionHandler(ThrottledException.class)
  ResponseEntity<?> throttled() {
    return ResponseEntity.status(429)
        .header("Retry-After", "900")
        .body(Map.of("message", "Too many login attempts. Please try again later."));
  }
}
