package com.example.helloauth.service;

import com.example.helloauth.settings.AppProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The stub the PRD calls for: logs the reset link instead of sending it.
 *
 * <p>This writes a live reset token to the application log, which is exactly the kind of thing that
 * should never happen in production. It is tolerable here only because real email is out of scope
 * and there is no other way for a developer to complete the flow. It is also the reason the token
 * is stored hashed: the log is the weak point, and a log leak must not hand over the database's
 * copy as well.
 */
@Service
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    private final AppProperties properties;

    public LoggingEmailService(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendPasswordResetEmail(String email, String plaintextToken, Duration validFor) {
        String link =
                properties.frontendBaseUrl()
                        + "/reset-password?token="
                        + URLEncoder.encode(plaintextToken, StandardCharsets.UTF_8);
        log.warn(
                "EMAIL STUB — would send a password reset to {} (valid {} minutes): {}",
                email,
                validFor.toMinutes(),
                link);
    }
}
