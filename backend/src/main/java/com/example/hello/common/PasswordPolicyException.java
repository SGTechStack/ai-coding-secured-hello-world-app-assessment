package com.example.hello.common;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 400: the submitted password does not satisfy the strength policy. */
public class PasswordPolicyException extends ApiException {

  public PasswordPolicyException(List<String> violations) {
    super(
        HttpStatus.BAD_REQUEST,
        "Password does not meet the strength policy",
        Map.of(
            "errors",
            violations.stream().map(v -> Map.of("field", "password", "message", v)).toList()));
  }
}
