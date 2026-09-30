package com.assessment.hello.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmDto(
        @NotBlank String token,
        @NotBlank @Size(min = 12, max = 100) String newPassword
) {
}
