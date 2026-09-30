package sg.securedhello.password;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.securedhello.credential.CredentialTokenRepository;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.security.PasswordProperties;
import sg.securedhello.user.PasswordHistoryEntry;
import sg.securedhello.user.PasswordHistoryRepository;
import sg.securedhello.user.TrustedDeviceRepository;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * The single path that sets any password (ADR-005): self-service change, forced-change completion, activation, reset
 * redemption call {@link #setPassword}, and the forced-change issuances (the bootstrap seed first) call
 * {@link #issueForcedChangeCredential}, which runs the same policy. No call site is exempt, and ArchUnit holds it to
 * being the only caller of {@code PasswordEncoder.encode()} and the only writer of the credential column (T-CRED-005).
 *
 * <p>It sets a password; it does not check one. A caller that must verify the current password first (the change
 * endpoint, ADR-008) does so itself, and a caller that ends sessions does so through {@code SessionTerminationService}
 * after this returns.
 */
@Service
public class PasswordService {

    private final PasswordPolicy policy;
    private final PasswordEncoder encoder;
    private final UserAccountRepository accounts;
    private final PasswordHistoryRepository history;
    private final CredentialTokenRepository tokens;
    private final TrustedDeviceRepository devices;
    private final int historyLength;
    private final Clock clock;

    PasswordService(PasswordPolicy policy, PasswordEncoder encoder, UserAccountRepository accounts,
            PasswordHistoryRepository history, CredentialTokenRepository tokens, TrustedDeviceRepository devices,
            PasswordProperties properties, Clock clock) {
        this.policy = policy;
        this.encoder = encoder;
        this.accounts = accounts;
        this.history = history;
        this.tokens = tokens;
        this.devices = devices;
        this.historyLength = properties.historyLength();
        this.clock = clock;
    }

    /**
     * Sets the account's password, in the caller's transaction if there is one:
     * <ol>
     *   <li>normalises it to NFC and runs the policy, rules in order (ADR-005), the last being reuse of the current
     *       password or one of the retained hashes before it;</li>
     *   <li>encodes it with the BCrypt {@code DelegatingPasswordEncoder} (ADR-001; no pepper, ADR-004) and stores it,
     *       which also completes any forced change (ADR-046);</li>
     *   <li>records it in the history and trims the history to {@code history-length} (T-CRED-021);</li>
     *   <li>invalidates the account's pending reset tokens (ADR-007);</li>
     *   <li>revokes every trusted device of the account, so no device cookie issued under the old password keeps its
     *       own lockout lane (ADR-075).</li>
     * </ol>
     *
     * @throws PasswordRejectedException if a rule refuses it; the transaction rolls back and nothing changes
     * @throws IllegalArgumentException  if no account has {@code accountId}
     */
    @Transactional
    public void setPassword(UUID accountId, String rawPassword) {
        set(accountId, rawPassword, null);
    }

    /**
     * Sets the account's password as a <em>forced-change credential</em> (ADR-046): exactly as {@link #setPassword},
     * then the account's {@code force_password_change} flag is set and {@code credential_issued_at} stamped with the
     * current time, so the holder must change it before anything else and it expires 30 days after issue. The
     * issuances that call this are the bootstrap seed (ADR-047) and the recovery runner's single form (ADR-073); an
     * admin re-enable re-issues the current password through {@link #issueForcedChangeCredential(UUID)}.
     *
     * @throws PasswordRejectedException if a rule refuses it; the transaction rolls back and nothing changes
     * @throws IllegalArgumentException  if no account has {@code accountId}
     */
    @Transactional
    public void issueForcedChangeCredential(UUID accountId, String rawPassword) {
        set(accountId, rawPassword, clock.instant());
    }

    /**
     * Re-issues the account's current password as a forced-change credential (ADR-046), for an admin re-enable: the
     * user's own prior password, so it runs no policy and writes no history (ADR-008), but the flag is set and the
     * 30-day clock starts now. An account that has no password yet (never activated) is left as it is: its activation
     * sets one.
     *
     * @throws IllegalArgumentException if no account has {@code accountId}
     */
    @Transactional
    public void issueForcedChangeCredential(UUID accountId) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("No account " + accountId));
        String current = account.getPasswordHash();
        if (current != null) {
            account.issueCredential(current, clock.instant());
        }
    }

    /**
     * Invalidates the account's password, for the recovery runner's batch form, which mints nothing (ADR-073): the
     * credential column and any forced change are cleared, so no password signs in, and the pending reset tokens are
     * cancelled, and every trusted device is revoked (ADR-075). The account recovers through the normal reset flow,
     * whose redemption sets a new password. The retained history stays, so the old password cannot simply be set back.
     *
     * @throws IllegalArgumentException if no account has {@code accountId}
     */
    @Transactional
    public void invalidate(UUID accountId) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("No account " + accountId));
        account.revokeCredential();
        tokens.deletePending(accountId, CredentialTokenType.PASSWORD_RESET);
        devices.revokeAll(accountId);
    }

    /**
     * The rule an operator-supplied password would fail for a new account with {@code username} and {@code email}, or
     * empty if it passes: the same policy {@link #setPassword} runs, with no history yet to reuse. The bootstrap checks
     * its seed password with it during context refresh, before any account exists (ADR-047).
     */
    public Optional<PasswordRule> rejection(String rawPassword, String username, String email) {
        return policy.check(PasswordPolicy.normalise(rawPassword), new PasswordPolicy.Account(username, email),
                candidate -> false);
    }

    private void set(UUID accountId, String rawPassword, @Nullable Instant issuedAt) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("No account " + accountId));
        String password = PasswordPolicy.normalise(rawPassword);
        List<PasswordHistoryEntry> retained = history.findByUserIdOrderByCreatedAtDesc(accountId);
        policy.check(password, new PasswordPolicy.Account(account.getUsername(), account.getEmail()),
                        candidate -> reuses(candidate, account.getPasswordHash(), retained))
                .ifPresent(rule -> {
                    throw new PasswordRejectedException(rule);
                });

        String encoded = encoder.encode(password);
        if (issuedAt == null) {
            account.replacePasswordHash(encoded);
        } else {
            account.issueCredential(encoded, issuedAt);
        }
        history.save(new PasswordHistoryEntry(accountId, encoded, clock.instant()));
        history.deleteAll(retained.stream().skip(historyLength - 1L).toList());
        tokens.deletePending(accountId, CredentialTokenType.PASSWORD_RESET);
        devices.revokeAll(accountId);
    }

    /**
     * Whether {@code candidate} matches the current hash or one of the newest retained hashes, {@code history-length}
     * in all. The current hash is normally the newest history row too, and is compared once; an account whose
     * password was never set here (a fixture) still has its current one checked.
     */
    private boolean reuses(String candidate, @Nullable String current, List<PasswordHistoryEntry> retained) {
        return Stream.concat(Stream.ofNullable(current),
                        retained.stream().map(PasswordHistoryEntry::getPasswordHash).filter(hash -> !hash.equals(current)))
                .limit(historyLength)
                .anyMatch(hash -> encoder.matches(candidate, hash));
    }
}
