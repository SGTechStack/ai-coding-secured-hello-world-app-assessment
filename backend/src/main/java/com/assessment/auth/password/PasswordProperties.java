package com.assessment.auth.password;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Password policy configuration (spec.md S5).
 *
 * @param maxLength 72 is a hard limit, not a preference: BCrypt in the pinned Spring Security 7.0.6
 *     <em>throws</em> above 72 bytes, so Questions.md:296's recommended 128 and the recipe's 1024
 *     both turn a long passphrase into a 500. ASCII-only makes a 72-character cap an exact byte cap.
 * @param historyDepth 4 — current plus three previous, the literal reading of Std:355. This makes
 *     Self-Service:269-272's verification procedure wrong for us; it must not be transcribed.
 */
@ConfigurationProperties(prefix = "app.password")
public record PasswordProperties(
    int minLength, int maxLength, int bcryptStrength, int historyDepth) {}
