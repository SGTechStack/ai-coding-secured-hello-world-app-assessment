package org.eds.demo.user.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import org.eds.demo.user.domain.Role;

/** The new Account plus its Temporary Password, which is returned this once and never again. */
// spotless:off
@Builder
public record CreatedAccountResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    String username,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    Role role,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
        description = "Shown once; hand it to the account holder. It cannot be retrieved later.")
    String temporaryPassword,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    Instant temporaryPasswordExpiresAt
) {

  @Override
  public String toString() {
    return "CreatedAccountResponse[username=" + username + ", role=" + role + "]";
  }
}
// spotless:on
