package org.eds.demo.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

// spotless:off
@Builder
public record SignInResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    String username,

    /** True while the holder must choose a new password before anything else is allowed. */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    boolean mustChangePassword
) {}
// spotless:on
