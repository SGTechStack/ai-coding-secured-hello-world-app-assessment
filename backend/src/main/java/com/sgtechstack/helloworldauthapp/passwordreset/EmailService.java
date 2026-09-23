package com.sgtechstack.helloworldauthapp.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stub implementation: real SMTP integration is out of scope for this build
 * (see spec), so nothing is actually delivered.
 *
 * <p>The reset link is deliberately <em>not</em> logged. It carries a live,
 * single-use token that is equivalent to the account's password until it
 * expires, and application logs get read by operators, tailed by CI jobs and
 * shipped to aggregators that have no business holding one. Logging it turns
 * "can read logs" into "can take over any account that has requested a
 * reset".
 *
 * <p>Walking the reset flow locally still needs the link, since there is no
 * mail server to pick it up from. Set
 * {@code app.mail.log-reset-link=true} to surface it. That flag defaults to
 * false in every profile — including dev — because dev is the profile this
 * application is normally started with, so defaulting it on there would leave
 * the leak in place everywhere it actually runs.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final boolean logResetLink;

    public EmailService(@Value("${app.mail.log-reset-link:false}") boolean logResetLink) {
        this.logResetLink = logResetLink;
    }

    /**
     * "Sends" the reset email. Records that a dispatch happened without
     * recording the recipient or the token: the audit question worth
     * answering here is whether the reset path ran, and neither the address
     * nor the secret is needed to answer it.
     */
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.info("Password reset email (stub) dispatched");

        if (logResetLink) {
            log.warn("DEV ONLY (app.mail.log-reset-link=true) — reset link for {}: {}", toEmail, resetLink);
        }
    }
}
