package com.example.authapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Stub: logs the reset link instead of sending mail. DEV ONLY - the link contains a live token. */
@Service
public class LoggingEmailService implements EmailService {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        LOG.info("[stub email] to={} password reset link: {}", AuditLogger.sanitize(toEmail), resetLink);
    }
}
