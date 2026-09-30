package com.assessment.hello.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stubbed email service. In a real deployment this would integrate with an SMTP
 * provider; here it simply logs the reset link so the flow can be exercised in dev.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        // NOTE: we log the raw token here only because this is a stub for local dev.
        // A real implementation would email a link and never log the token.
        String link = "http://localhost:3000/reset-password?token=" + resetToken;
        log.info("[EmailService STUB] Password reset link for {}: {}", toEmail, link);
    }
}
