package com.example.hello.auth;

import com.example.hello.shared.ApiException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
  public void validate(String password) {
    if (password == null
        || password.codePointCount(0, password.length()) < 12
        || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Password must contain at least 12 characters and at most 72 UTF-8 bytes.");
    }
  }
}
