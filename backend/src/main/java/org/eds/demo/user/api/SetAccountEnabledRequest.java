package org.eds.demo.user.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Admin request to enable or disable an Account. */
// spotless:off
public record SetAccountEnabledRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    Boolean enabled
) {}
// spotless:on
