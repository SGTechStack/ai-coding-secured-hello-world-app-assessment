package com.eitri.passwordreset;

/** Sends account emails. The only implementation is {@link LoggingEmailService}, a development stub. */
public interface EmailService {

    void sendPasswordResetEmail(String email, String resetLink);
}
