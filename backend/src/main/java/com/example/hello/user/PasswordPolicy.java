package com.example.hello.user;

import com.example.hello.common.PasswordPolicyException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Password strength policy from the PRD (length &ge; 12). The upper bound is the BCrypt
 * input limit of 72 bytes. Applied identically to registration, password reset and the
 * bootstrapped admin.
 */
@Component
public class PasswordPolicy {

  public static final int MIN_LENGTH = 12;
  public static final int MAX_LENGTH = 72;

  public List<String> violations(String password, String username) {
    List<String> violations = new ArrayList<>();
    if (password == null || password.length() < MIN_LENGTH) {
      violations.add("must be at least " + MIN_LENGTH + " characters long");
      return violations;
    }
    if (password.length() > MAX_LENGTH) {
      violations.add("must be at most " + MAX_LENGTH + " characters long");
    }
    if (password.isBlank()) {
      violations.add("must not be blank");
    }
    if (username != null
        && !username.isBlank()
        && password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
      violations.add("must not contain the username");
    }
    return violations;
  }

  public void validate(String password, String username) {
    List<String> violations = violations(password, username);
    if (!violations.isEmpty()) {
      throw new PasswordPolicyException(violations);
    }
  }
}
