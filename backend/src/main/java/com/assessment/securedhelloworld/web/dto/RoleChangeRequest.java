package com.assessment.securedhelloworld.web.dto;

import com.assessment.securedhelloworld.domain.Role;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(@NotNull Role role) {
}
