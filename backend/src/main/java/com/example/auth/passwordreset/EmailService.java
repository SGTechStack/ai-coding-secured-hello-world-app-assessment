package com.example.auth.passwordreset;

/** Abstracts outbound email so {@link PasswordResetService} doesn't depend on a real mail transport. */
public interface EmailService {

    void sendPasswordResetEmail(String toEmail, String resetLink);
}
