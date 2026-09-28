package com.example.auth.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Stub mail transport: logs the reset link instead of sending a real email.
 * The PRD asks for this to be stubbed for now; swapping in a real transport
 * (SMTP, SES, etc.) later only requires a new {@link EmailService}
 * implementation, not any change to {@link PasswordResetService}.
 *
 * <p>Restricted to {@code dev} -- every working password-reset link is a
 * live credential, so logging it at INFO must never happen in prod. There is
 * deliberately no non-profile-gated {@link EmailService} bean: prod fails to
 * start ({@code NoSuchBeanDefinitionException}) until a real transport is
 * wired in, rather than silently falling back to logging real users' reset
 * links.
 */
@Service
@Profile("dev")
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.info("Password reset link for {}: {}", toEmail, resetLink);
    }
}
