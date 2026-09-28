package com.example.helloauth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stub email service. Instead of sending real mail it logs the reset link.
 * Real SMTP is out of scope per the PRD.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public void sendPasswordResetEmail(String email, String resetLink) {
        // The link contains a single-use plaintext token delivered only to the (stubbed) recipient.
        // In this demo it is logged so the flow is testable; it is not an audit log line.
        log.info("[EmailService STUB] Password reset for {} -> {}", email, resetLink);
    }
}
