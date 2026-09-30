package sg.securedhello.audit;

/**
 * Why an administrator unlocked an account (REJ-028): a closed enum, so no free text or newline can reach an audit row.
 * It travels only in the unlock row's {@code user.target.unlock_reason}, never in a column (R-AUD-004).
 */
public enum UnlockReason {

    /** The user asked to be let back in before the lock lifted. */
    USER_REQUEST,

    /** The failures that locked the account were not an attack. */
    FALSE_POSITIVE,

    /** The user has since completed a password reset. */
    PASSWORD_RESET_COMPLETED,

    /** Anything else. */
    OTHER
}
