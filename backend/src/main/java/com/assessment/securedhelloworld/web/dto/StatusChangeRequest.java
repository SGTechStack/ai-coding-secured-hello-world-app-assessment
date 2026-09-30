package com.assessment.securedhelloworld.web.dto;

import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(@NotNull Boolean enabled) {
}
