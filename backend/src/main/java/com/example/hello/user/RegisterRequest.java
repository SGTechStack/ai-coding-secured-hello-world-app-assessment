package com.example.hello.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(
            regexp = "^[A-Za-z0-9._-]+$",
            message = "may only contain letters, digits, '.', '_' and '-'")
        String username,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        String password) {

  /** Never expose the password through accidental logging of the request. */
  @Override
  public String toString() {
    return "RegisterRequest{username='" + username + "', email='" + email + "', password='***'}";
  }
}
