package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.User;

/**
 * Outbound email port. Scope for this project is a stub (no real SMTP): the
 * logging implementation records the reset link instead of sending mail.
 */
public interface EmailService {

    /**
     * Delivers a password reset link to the user. The {@code plaintextToken} is
     * the single-use token; only its hash is ever persisted, so this is the one
     * place the plaintext leaves the service layer.
     */
    void sendPasswordResetEmail(User user, String plaintextToken);
}
