package com.assessment.securedhelloworld.web.dto;

public record LoginResponse(String username, String role, boolean forcePasswordChange) {
}
