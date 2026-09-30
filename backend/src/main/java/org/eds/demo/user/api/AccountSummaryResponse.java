package org.eds.demo.user.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import org.eds.demo.user.domain.Role;

/** One row of the admin Account list. */
// spotless:off
@Builder
public record AccountSummaryResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    String username,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    Role role,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    boolean enabled,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    Instant createdAt
) {}
// spotless:on
