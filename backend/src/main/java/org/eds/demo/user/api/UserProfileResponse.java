package org.eds.demo.user.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.Builder;

// spotless:off
@Builder
public record UserProfileResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    String username,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    String displayName,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    Set<String> roles
) {}
// spotless:on
