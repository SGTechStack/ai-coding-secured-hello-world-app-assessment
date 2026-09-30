package com.assessment.securedhelloworld.web.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmRequest(@NotBlank String token, @NotBlank String newPassword) {
}
