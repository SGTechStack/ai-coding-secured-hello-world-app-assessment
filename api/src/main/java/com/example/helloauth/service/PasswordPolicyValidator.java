package com.example.helloauth.service;

import com.example.helloauth.settings.AppProperties;
import com.example.helloauth.service.exception.AuthExceptions.WeakPasswordException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * The password policy, in one place, applied identically wherever a password is set: registration,
 * password reset, and the admin seed. The PRD's policy is length and nothing else — no composition
 * rules, which current guidance regards as counter-productive anyway.
 */
@Component
public class PasswordPolicyValidator {

    private final AppProperties properties;

    public PasswordPolicyValidator(AppProperties properties) {
        this.properties = properties;
    }

    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new WeakPasswordException("Password is required.");
        }
        int minLength = properties.passwordPolicy().minLength();
        if (rawPassword.length() < minLength) {
            throw new WeakPasswordException(
                    "Password must be at least " + minLength + " characters long.");
        }
        // Measured in bytes, not characters: BCrypt's limit is 72 *bytes*, and a password of
        // emoji or accented characters hits it well before 72 characters.
        int maxBytes = properties.passwordPolicy().maxLength();
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new WeakPasswordException(
                    "Password must be at most " + maxBytes + " bytes long.");
        }
    }
}
