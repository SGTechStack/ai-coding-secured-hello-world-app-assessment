package com.example.auth.passwordreset;

/**
 * Abstraction over outbound email. In this build the only message is the
 * password-reset link. Real SMTP delivery is out of scope (PRD); the default
 * implementation is a logging stub.
 */
public interface EmailService {

    void sendPasswordResetEmail(String email, String resetLink);
}
