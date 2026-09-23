package com.assessment.securedhelloworld.passwordreset;

/**
 * Sends password-reset notifications. Real SMTP delivery is out of scope
 * for this build; {@link LoggingEmailService} is the only implementation,
 * logging the reset link instead of sending mail.
 */
public interface EmailService {

    void sendPasswordResetEmail(String toAddress, String resetLink);
}
