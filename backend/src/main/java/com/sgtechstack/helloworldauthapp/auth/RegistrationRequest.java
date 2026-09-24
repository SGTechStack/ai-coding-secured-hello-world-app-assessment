package com.sgtechstack.helloworldauthapp.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /api/auth/register}. Password strength beyond
 * minimum length (see {@link PasswordPolicy}) is enforced in the service
 * layer, not here, so the specific policy can evolve without touching the
 * wire contract.
 *
 * <h2>Why the username has a character allow-list</h2>
 *
 * {@code @Size} bounded the length and nothing bounded the content, so a
 * username could contain newlines, ANSI escape sequences or NUL bytes. Since
 * the username is the subject of most audit lines this application writes, that
 * made the audit log writable by the person it was meant to hold accountable:
 * register as {@code "alice\nLogin succeeded username=admin"} and every log line
 * about that account emits a second, fabricated record underneath it.
 *
 * <p>The allow-list is the primary fix, because it stops such a username
 * existing rather than papering over it at each log call. {@code LogSafe} still
 * sanitises at the call sites, for two reasons: login accepts a username
 * parameter that was never validated (an unknown username is a normal thing to
 * log, and validating it there would leak which format the system accepts), and
 * a control that depends on every future field being remembered is a control
 * that eventually is not.
 *
 * <p>Letters, digits, dot, underscore and hyphen. Deliberately narrow: this is
 * an allow-list, so the failure mode of being too strict is a rejected
 * registration with a clear message, while the failure mode of being too
 * permissive is silent. Excluding {@code @} also keeps usernames and email
 * addresses structurally distinguishable, so neither can be passed where the
 * other is expected.
 */
public record RegistrationRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 64, message = "Username must be between 3 and 64 characters")
        @Pattern(
                regexp = "^[A-Za-z0-9._-]+$",
                message = "Username may contain only letters, digits, dots, underscores and hyphens")
        String username,

        @NotBlank(message = "Email is required")
        // 254 is the maximum length of a deliverable address (RFC 5321's
        // envelope limit). Bounded here as well as by @Email because @Email
        // checks shape, not size, and this value is stored and logged about.
        @Size(max = 254, message = "Email must be at most 254 characters")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
}
