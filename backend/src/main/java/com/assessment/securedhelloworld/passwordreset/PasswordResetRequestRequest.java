package com.assessment.securedhelloworld.passwordreset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

/**
 * Request body for {@code POST /api/password-reset/request}.
 */
public class PasswordResetRequestRequest {

    @NotBlank
    @Email
    private String email;

    public PasswordResetRequestRequest() {
        // Jackson
    }

    public PasswordResetRequestRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
