package com.assessment.securedhelloworld.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-service password change for an already-authenticated user.
 * Requires the current password as confirmation (distinct from the
 * token-based {@code PasswordResetConfirmRequest} flow, which is for
 * users who cannot log in at all).
 */
public class PasswordChangeRequest {

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 12, message = "Password must be at least 12 characters long")
    private String newPassword;

    public PasswordChangeRequest() {
        // Jackson
    }

    public PasswordChangeRequest(String currentPassword, String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
