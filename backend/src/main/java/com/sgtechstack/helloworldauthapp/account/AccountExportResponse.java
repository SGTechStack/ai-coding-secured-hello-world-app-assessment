package com.sgtechstack.helloworldauthapp.account;

import com.sgtechstack.helloworldauthapp.privacy.PrivacyProperties;
import com.sgtechstack.helloworldauthapp.user.Role;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything this application holds about the requesting account, in one
 * machine-readable document.
 *
 * <h2>Completeness is the point</h2>
 *
 * An export that omits a field is worse than no export, because it makes a false
 * claim: the user reads it as the full picture. So this deliberately includes the
 * unglamorous operational columns — the failed-login counter, the lockout
 * timestamp, the reset-request history — and not just the profile fields somebody
 * would think to list. If a column exists on the account, it appears here.
 *
 * <p>Two things are excluded, both with reasons rather than by omission. The
 * password hash is not the user's data in any useful sense; it is a credential
 * verifier, and handing over a BCrypt hash gives an offline cracking target while
 * telling its owner nothing they do not already know. And the audit records are
 * not included: they are about actions taken by administrators, are retained for
 * accountability under a different basis, and handing them over on request would
 * make the accountability record something the accountable party can extract.
 *
 * <p>Reset tokens appear as metadata only ({@link ResetRequestRecord}) — when a
 * reset was asked for and whether it was used, never the token hash. The history
 * is the part with meaning to the user; the hash is a secret verifier.
 *
 * @param notice the privacy notice in force, embedded rather than linked so the
 *               downloaded file states on its face what the data is for and how
 *               long it is kept
 */
public record AccountExportResponse(
        Instant generatedAt,
        UUID id,
        String username,
        String email,
        Role role,
        boolean enabled,
        Instant createdAt,
        int failedLoginAttempts,
        Instant lockedUntil,
        Instant lastFailedLoginAt,
        List<ResetRequestRecord> passwordResetRequests,
        PrivacyProperties notice
) {

    /**
     * One password-reset request, as history rather than as a credential.
     *
     * @param usedAt null when the link was never followed
     */
    public record ResetRequestRecord(Instant expiresAt, Instant usedAt) {
    }
}
