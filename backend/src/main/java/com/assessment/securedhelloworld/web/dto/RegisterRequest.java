package com.assessment.securedhelloworld.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String password
) {
}
