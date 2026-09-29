package com.example.securedhello.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stub email delivery. Instead of sending mail, it logs the password-reset
 * link so the flow is demoable in development. Real SMTP is out of scope.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /**
     * "Sends" a password-reset link. The link carries the one-time plaintext
     * token; this is the only place the plaintext appears, and only in a local
     * dev log — never persisted.
     */
    public void sendPasswordResetEmail(String email, String resetLink) {
        log.info("[stub-email] password reset link for {}: {}", email, resetLink);
    }
}
