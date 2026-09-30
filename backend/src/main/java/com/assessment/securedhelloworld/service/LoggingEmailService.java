package com.assessment.securedhelloworld.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stub {@link EmailService}: logs instead of sending real mail (PRD "Out of Scope" / Story 6-7).
 *
 * <p>The logger category is deliberately the interface's FQCN ({@code
 * com.assessment.securedhelloworld.service.EmailService}), not this class, because
 * {@code application.yml}/{@code application-dev.yml} raise exactly that category to DEBUG only
 * under the {@code dev} profile. The fact that a reset email would be sent is logged at INFO in
 * every profile (no token, no link — safe to leave on always); the {@code resetLink}, which embeds
 * the plaintext token, is logged at DEBUG only, so the plaintext token can never leak into a
 * non-dev log stream (IM8 lm-19).
 */
@Service
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger("com.assessment.securedhelloworld.service.EmailService");

    @Override
    public void sendPasswordResetEmail(String email, String resetLink) {
        log.info("Password reset email would be sent to {}", email);
        log.debug("Password reset link for {}: {}", email, resetLink);
    }
}
