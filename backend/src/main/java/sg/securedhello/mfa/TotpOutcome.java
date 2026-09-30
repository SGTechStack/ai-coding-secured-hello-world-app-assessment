package sg.securedhello.mfa;

/**
 * What one factor verification found, decided under the row locks (ADR-027): {@link TotpVerification} answers and
 * audits by it, and {@link TotpUserDetails#fail} reports a counted failure as one of its failure outcomes.
 */
enum TotpOutcome {
    /** The code verified. */
    VERIFIED,
    /** A wrong code, counted unless empty, below both thresholds. */
    WRONG,
    /** The factor was already locked; nothing was checked. */
    LOCKED,
    /** A wrong code that locked the factor (tier 1). */
    LOCKING,
    /** A wrong code that disabled the factor (tier 2). */
    DISABLING
}
