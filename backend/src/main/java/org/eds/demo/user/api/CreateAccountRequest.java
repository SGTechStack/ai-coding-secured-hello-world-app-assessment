package org.eds.demo.user.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eds.demo.user.domain.Role;

/** Admin request to create an Account; the server generates the Temporary Password. */
// spotless:off
public record CreateAccountRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 100)
    String username,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    Role role
) {}
// spotless:on
