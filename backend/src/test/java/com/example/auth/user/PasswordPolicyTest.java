package com.example.auth.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for PasswordPolicy — the single source of truth for the
 * password policy (length 12-72, 3-of-4 character categories).
 */
class PasswordPolicyTest {

    // ── length ────────────────────────────────────────────────────────────────

    @Test
    void passwordOf11Chars_isRejected() {
        // Below minimum length; 3 categories but too short
        assertThat(PasswordPolicy.validate("Abc-1234567")).isNotEmpty();
    }

    @Test
    void passwordOf12Chars_withCategories_isAccepted() {
        // Exactly minimum length; lower + upper + digit = 3 categories
        assertThat(PasswordPolicy.validate("abcABC123456")).isEmpty();
    }

    @Test
    void passwordOf72Chars_withCategories_isAccepted() {
        // Exactly maximum length; lower + digit + special = 3 categories
        String pw = "a1-" + "a".repeat(69); // length 72
        assertThat(pw).hasSize(72);
        assertThat(PasswordPolicy.validate(pw)).isEmpty();
    }

    @Test
    void passwordOf73Chars_isRejected() {
        String pw = "a1-" + "a".repeat(70); // length 73
        assertThat(pw).hasSize(73);
        assertThat(PasswordPolicy.validate(pw)).isNotEmpty();
    }

    // ── complexity — insufficient categories ─────────────────────────────────

    @Test
    void onlyLowercase_isRejected() {
        // Only 1 category — lowercase
        assertThat(PasswordPolicy.validate("alllowercase12")).isNotEmpty();
        // Note: "alllowercase12" has digit too → 2 categories → still rejected
        assertThat(PasswordPolicy.validate("alllowercaseee")).isNotEmpty(); // 1 category
    }

    @Test
    void lowercaseAndUppercase_only_isRejected() {
        // Only 2 categories — missing digit and special
        assertThat(PasswordPolicy.validate("AllLowerUpper")).isNotEmpty();
    }

    @Test
    void lowercaseAndDigit_only_isRejected() {
        // Only 2 categories — missing upper and special
        assertThat(PasswordPolicy.validate("lowercase12345")).isNotEmpty();
    }

    // ── complexity — valid combinations of 3 categories ─────────────────────

    @Test
    void lowercase_uppercase_digit_isAccepted() {
        assertThat(PasswordPolicy.validate("LowerUpper1234")).isEmpty();
    }

    @Test
    void lowercase_digit_special_isAccepted() {
        // 3 categories: lower + digit + special
        assertThat(PasswordPolicy.validate("lowercase123!!!")).isEmpty();
    }

    @Test
    void uppercase_digit_special_isAccepted() {
        // 3 categories: upper + digit + special
        assertThat(PasswordPolicy.validate("UPPERCASE123!!!")).isEmpty();
    }

    @Test
    void lowercase_uppercase_special_isAccepted() {
        // 3 categories: lower + upper + special
        assertThat(PasswordPolicy.validate("lowerUPPER!!!!!!")).isEmpty();
    }

    @Test
    void allFourCategories_isAccepted() {
        // lower + upper + digit + special = 4 categories
        assertThat(PasswordPolicy.validate("LowerUpper123!!!")).isEmpty();
    }

    // ── violation messages must not contain the password ─────────────────────

    @Test
    void violationMessages_doNotContainTheSubmittedPassword() {
        String submitted = "weakpass";
        var violations = PasswordPolicy.validate(submitted);
        for (String message : violations) {
            assertThat(message).doesNotContain(submitted);
        }
    }

    // ── common passwords used in existing tests still pass ───────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "secure-pass-12",      // lower + special + digit → 3
        "brand-new-pass-34",   // lower + special + digit → 3
        "boot-admin-pass-99",  // lower + special + digit → 3
        "a-strong-prod-password-99" // lower + special + digit → 3
    })
    void existingTestPasswords_stillAccepted(String password) {
        assertThat(PasswordPolicy.validate(password)).isEmpty();
    }
}
