package com.sgtechstack.helloworldauthapp.auth;

import java.util.List;

/**
 * Uniform error payload for validation and conflict responses.
 */
public record ErrorResponse(String message, List<String> details) {

    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, List.of());
    }

    public static ErrorResponse of(String message, List<String> details) {
        return new ErrorResponse(message, details);
    }
}
