package org.eds.demo.user.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import org.eds.demo.user.application.AccountSummary;
import org.eds.demo.user.domain.Role;

/** One row of the admin Account list. */
// spotless:off
@Builder
public record AccountSummaryResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    String username,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    Role role,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    boolean enabled,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    Instant createdAt
) {

  public static AccountSummaryResponse from(AccountSummary account) {
    return AccountSummaryResponse.builder()
        .id(account.id())
        .username(account.username())
        .role(account.role())
        .enabled(account.enabled())
        .createdAt(account.createdAt())
        .build();
  }
}
// spotless:on
