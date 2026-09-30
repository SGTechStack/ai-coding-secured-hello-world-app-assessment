package org.eds.demo.user.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.eds.demo.user.domain.Role;

/** Admin request to give an Account a different Role. */
// spotless:off
public record ChangeAccountRoleRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    Role role
) {}
// spotless:on
