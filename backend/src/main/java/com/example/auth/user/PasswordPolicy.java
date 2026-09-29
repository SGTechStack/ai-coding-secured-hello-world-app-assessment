package com.example.auth.user;

import java.util.ArrayList;
import java.util.List;

/**
 * Authoritative password policy for the application.
 *
 * Policy (IM8-aligned):
 *  - Length: 12–72 characters (72 is BCrypt's byte cap — silent truncation above it).
 *  - Complexity: characters from at least 3 of the 4 categories:
 *      uppercase (A-Z), lowercase (a-z), digits (0-9), special (any non-alphanumeric).
 *
 * This class is the single source of truth. It is referenced by:
 *  - {@link PasswordPolicyValidator} (Bean Validation, used on DTOs).
 *  - {@link com.example.auth.admin.AdminBootstrapRunner} (startup validation).
 *
 * The actual password string is never retained or included in any violation message
 * so it cannot leak through error responses or logs.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 72;   // BCrypt byte cap
    public static final int MIN_CATEGORIES = 3;

    private PasswordPolicy() {}

    /**
     * Validates the password against the policy.
     * @return empty list if valid; non-empty list of human-readable violation messages otherwise.
     *         Messages never include the submitted password value.
     */
    public static List<String> validate(String password) {
        List<String> violations = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            violations.add("Password must not be blank.");
            return violations;
        }

        // Length checks
        if (password.length() < MIN_LENGTH) {
            violations.add("Password must be at least " + MIN_LENGTH + " characters.");
        }
        if (password.length() > MAX_LENGTH) {
            violations.add("Password must not exceed " + MAX_LENGTH + " characters.");
        }

        // Complexity: count how many of the 4 categories are present
        boolean hasUpper   = password.chars().anyMatch(c -> c >= 'A' && c <= 'Z');
        boolean hasLower   = password.chars().anyMatch(c -> c >= 'a' && c <= 'z');
        boolean hasDigit   = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        int categories = (hasUpper ? 1 : 0) + (hasLower ? 1 : 0)
                       + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);

        if (categories < MIN_CATEGORIES) {
            violations.add(
                "Password must contain characters from at least " + MIN_CATEGORIES
                + " of the following categories: uppercase letters (A-Z), lowercase letters (a-z),"
                + " numbers (0-9), special characters.");
        }

        return violations;
    }

    /** Convenience: returns true when the password is valid. */
    public static boolean isValid(String password) {
        return validate(password).isEmpty();
    }
}
