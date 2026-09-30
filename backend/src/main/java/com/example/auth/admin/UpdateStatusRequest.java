package com.example.auth.admin;

import jakarta.validation.constraints.NotNull;

/** {@code Boolean}, not {@code boolean}: a missing field must be a 400, not a silent "disable". */
public record UpdateStatusRequest(@NotNull(message = "enabled is required") Boolean enabled) {}
