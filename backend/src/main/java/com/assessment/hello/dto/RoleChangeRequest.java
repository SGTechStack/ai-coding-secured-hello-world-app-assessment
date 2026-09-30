package com.assessment.hello.dto;

import com.assessment.hello.domain.Role;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(
        @NotNull Role role
) {
}
