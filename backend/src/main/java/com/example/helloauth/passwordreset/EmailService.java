package com.example.helloauth.passwordreset;

import com.example.helloauth.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The stubbed email adapter — there is no SMTP in this app (spec). Sending
 * means logging the reset link a real deployment would deliver out-of-band;
 * the logged {@code /reset-password?token=…} URL is what a developer copies
 * into the SPA to exercise the flow.
 *
 * <p>This is deliberately the only place the plaintext token is emitted —
 * the token is a credential, so nothing else (including audit logs, ticket
 * 14) may log it. Because the token is a live bearer credential, the link
 * line is gated on {@code app.password-reset.log-reset-link}
 * (security-review F-02): {@code true} in the default profile where a
 * developer reads the console, {@code false} under {@code prod}, where logs
 * typically ship to aggregation. Suppressed mode still records the request —
 * naming the recipient, never the token — so the flow stays observable.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final AppProperties properties;

    public EmailService(AppProperties properties) {
        this.properties = properties;
    }

    /**
     * Delivers the reset link. With {@code log-reset-link} on, logs the SPA
     * URL carrying the plaintext token; with it off, logs a suppressed-
     * delivery line that names the recipient but contains no token material.
     */
    public void sendPasswordResetLink(String toEmail, String token) {
        if (properties.getPasswordReset().isLogResetLink()) {
            String link = properties.getPasswordReset().getLinkBaseUrl()
                + "?token=" + token;
            log.info("Password reset link for {}: {}", toEmail, link);
        } else {
            log.info("Password reset requested for {} — no email adapter "
                + "configured; reset link suppressed", toEmail);
        }
    }
}
