package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stub {@link EmailService} that logs the reset link instead of sending mail.
 * The link contains the plaintext token, so this is logged at the application
 * logger (not the {@code audit} logger, which must never carry the token).
 */
@Service
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    private final String resetUrlBase;

    public LoggingEmailService(
        @Value("${app.password-reset.reset-url-base:http://localhost:3000/reset-password}") String resetUrlBase) {
        this.resetUrlBase = resetUrlBase;
    }

    @Override
    public void sendPasswordResetEmail(User user, String plaintextToken) {
        String link = resetUrlBase + "?token=" + plaintextToken;
        // Stub delivery: in production this would hand off to an SMTP/provider client.
        log.info("[email-stub] password reset link for {}: {}", user.getEmail(), link);
    }
}
