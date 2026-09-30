package com.assessment.securedhelloworld.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
