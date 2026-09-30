package com.example.hello.passwordreset;

import com.example.hello.user.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
    @NotBlank @Size(max = 256) String token,
    @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        String newPassword) {

  @Override
  public String toString() {
    return "PasswordResetConfirmRequest{token='***', newPassword='***'}";
  }
}
