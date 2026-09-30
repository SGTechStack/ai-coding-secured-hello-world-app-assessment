package sg.securedhello.recovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import sg.securedhello.audit.RecoveryRunContext;

/**
 * What a run would change, and its plan/apply digest (ADR-074): SHA-256 over a canonical tuple of non-secret state per
 * account, so an apply is refused when anything it previewed has moved since. Applying moves the state, so a digest can
 * never match twice, and a leftover invocation fires at most once (T-RUN-011).
 *
 * <p>The tuple is the ADR's list, plus whether a credential is set: without it, a batch invalidation of an account
 * with no cap and no issue time would leave its tuple as it was, and a leftover batch confirm could fire again.
 * {@code --operator} is deliberately left out, so one person can preview and another apply (ADR-074).
 *
 * @param scope         what the run rebinds
 * @param accounts      the accounts, in input order
 * @param inputDigest   the batch form's input-file digest, or {@code null} in the single form
 * @param database      the database the state was read from
 */
record RecoveryPlan(RecoveryScope scope, List<Account> accounts, @Nullable String inputDigest,
        RecoveryRunContext.Database database) {

    RecoveryPlan {
        accounts = List.copyOf(accounts);
    }

    /**
     * One account's state, as far as the digest and the run need it.
     *
     * @param id                 the account
     * @param username           its username, for the operator's output and the session kill
     * @param credentialSet      a password hash is stored
     * @param passwordDisabledAt when the NIST cap disabled the password, or {@code null}
     * @param credentialIssuedAt when a forced-change credential was issued, or {@code null}
     * @param factorCreatedAt    when the TOTP factor row was created, or {@code null} when there is none
     */
    record Account(UUID id, String username, boolean credentialSet, @Nullable Instant passwordDisabledAt,
            @Nullable Instant credentialIssuedAt, @Nullable Instant factorCreatedAt) {

        boolean hasFactor() {
            return factorCreatedAt != null;
        }
    }

    /** The digest the dry run prints and the apply must pass back: lower-case hex SHA-256. */
    String digest() {
        String tuples = accounts.stream().map(this::tuple).collect(Collectors.joining("\n"));
        return sha256(inputDigest == null ? tuples : tuples + "\ninput=" + inputDigest);
    }

    List<UUID> targets() {
        return accounts.stream().map(Account::id).toList();
    }

    /** The canonical tuple: every field named, absent values as {@code -}, instants in ISO-8601 UTC. */
    private String tuple(Account account) {
        return String.join("|", "account=" + account.id(), "scope=" + scope.code(),
                "password_disabled_at=" + text(account.passwordDisabledAt()),
                "credential_issued_at=" + text(account.credentialIssuedAt()),
                "credential_set=" + account.credentialSet(),
                "totp=" + (account.hasFactor() ? "present@" + account.factorCreatedAt() : "absent"),
                "schema=" + database.schemaVersion(), "database=" + database.path());
    }

    private static String text(@Nullable Instant instant) {
        return instant == null ? "-" : instant.toString();
    }

    /** Lower-case hex SHA-256 of {@code text} in UTF-8; also the batch input file's digest. */
    static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a required JDK algorithm", e);
        }
    }
}
