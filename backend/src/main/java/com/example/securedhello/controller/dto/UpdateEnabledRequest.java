package com.example.securedhello.controller.dto;

import jakarta.validation.constraints.NotNull;

/** Request body to enable or disable a target account. */
public record UpdateEnabledRequest(@NotNull Boolean enabled) {
}
