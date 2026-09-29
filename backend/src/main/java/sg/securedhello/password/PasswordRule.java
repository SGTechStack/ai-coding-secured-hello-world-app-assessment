package sg.securedhello.password;

/**
 * The password policy's rules, in the order {@link PasswordPolicy} runs them (ADR-005). A rejection names the first
 * rule that failed, as the {@code rule} member of a {@code PASSWORD_REJECTED} envelope.
 */
public enum PasswordRule {

    /** Under the minimum length, counted in code points after NFC (ADR-002). */
    MIN_LENGTH,
    /** Over the maximum length, counted in UTF-8 bytes after NFC (ADR-003). */
    MAX_BYTES,
    /** Exactly a breach-slice entry or a context word (REJ-004). */
    BLOCKLISTED,
    /** Contains the username, the email local part, the service name or a context word (ADR-005). */
    CONTEXT_TERM,
    /** A zxcvbn score below the configured minimum (ADR-005). */
    TOO_WEAK,
    /** Matches one of the retained hashes: the current password or one of the two before it (REJ-007). */
    HISTORY_REUSE
}
