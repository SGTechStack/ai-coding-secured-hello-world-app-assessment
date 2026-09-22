package com.sgtechstack.helloworldauthapp.admin;

import jakarta.validation.constraints.NotNull;

public record SetEnabledRequest(
        @NotNull(message = "Enabled is required")
        Boolean enabled
) {
}
