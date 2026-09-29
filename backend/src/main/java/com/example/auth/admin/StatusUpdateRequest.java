package com.example.auth.admin;

import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull Boolean enabled) {
}
