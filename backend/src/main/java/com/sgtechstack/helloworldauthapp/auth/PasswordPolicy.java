package com.sgtechstack.helloworldauthapp.auth;

import org.springframework.stereotype.Component;

/**
 * Minimum password strength policy: length only, per spec (≥ 12 characters).
 * Kept as its own component so the policy can grow (character-class
 * requirements, breached-password checks, etc.) without touching callers.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    public boolean isSatisfiedBy(String password) {
        return password != null && password.length() >= MIN_LENGTH;
    }
}
