package org.eds.demo.user.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The holder's chosen password, typed twice; the server checks that both entries match. */
// spotless:off
public record ChangePasswordRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Size(min = ChangePasswordRequest.MIN_PASSWORD_LENGTH, max = ChangePasswordRequest.MAX_PASSWORD_LENGTH)
    String newPassword,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    String confirmPassword
) {

  /** Password strength policy: the minimum number of characters. */
  public static final int MIN_PASSWORD_LENGTH = 12;

  /** BCrypt ignores input past 72 bytes; refuse absurd sizes instead of hashing them. */
  public static final int MAX_PASSWORD_LENGTH = 72;

  /** Keeps the plaintext passwords out of accidental logging. */
  @Override
  public String toString() {
    return "ChangePasswordRequest[redacted]";
  }
}
// spotless:on
