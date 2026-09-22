package com.sgtechstack.helloworldauthapp.admin;

import com.sgtechstack.helloworldauthapp.user.Role;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(
        @NotNull(message = "Role is required")
        Role role
) {
}
