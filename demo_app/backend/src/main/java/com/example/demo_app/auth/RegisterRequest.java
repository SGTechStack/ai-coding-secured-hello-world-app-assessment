package com.example.demo_app.auth;

import com.example.demo_app.user.AcceptablePassword;
import com.example.demo_app.user.NewAccount;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/register}. There is deliberately no {@code role} or {@code
 * enabled}: unknown JSON properties are ignored, so a body that sends them has no effect.
 *
 * <p>Each field's constraints share one message, so the field error is the same whichever of them
 * fails. The email and first name are trimmed before validation; the first name is otherwise kept
 * verbatim (any Unicode, HTML included) and encoded only at output.
 *
 * @param username 3 to 50 of {@code A-Z a-z 0-9 . _ -}; stored lowercase
 * @param email a valid address of at most 254 characters; stored lowercase
 * @param firstName 1 to 100 characters, no control characters
 * @param password must meet the password policy
 */
record RegisterRequest(
    @NotNull(message = USERNAME_RULE)
        @Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = USERNAME_RULE)
        String username,
    @NotBlank(message = EMAIL_RULE)
        @Email(message = EMAIL_RULE)
        @Size(max = 254, message = EMAIL_RULE)
        String email,
    @NotNull(message = FIRST_NAME_RULE)
        @Size(min = 1, max = 100, message = FIRST_NAME_RULE)
        @Pattern(regexp = "\\P{Cc}*", message = FIRST_NAME_RULE)
        String firstName,
    @AcceptablePassword String password) {

  static final String USERNAME_RULE =
      "Username must be 3 to 50 letters, digits, dots, hyphens or underscores.";
  static final String EMAIL_RULE = "Enter a valid email address.";
  static final String FIRST_NAME_RULE =
      "First name must be 1 to 100 characters, without control characters.";

  RegisterRequest {
    email = email == null ? null : email.strip();
    firstName = firstName == null ? null : firstName.strip();
  }

  NewAccount toNewAccount() {
    return new NewAccount(username, email, firstName, password);
  }

  /** Keeps the plaintext password out of any accidental log or error message. */
  @Override
  public String toString() {
    return "RegisterRequest[username=" + username + ", password=<redacted>]";
  }
}
