package com.sgtechstack.helloworldauthapp.audit;

/**
 * The set of actions that produce an audit record.
 *
 * <p>An enum rather than free-text strings so the vocabulary is closed: a
 * reader can enumerate every action that can ever appear in the table, and a
 * typo in a call site is a compile error rather than a category that silently
 * never matches a query.
 *
 * <p>Membership is limited to actions that are irreversible, privilege-changing
 * or personal-data-revealing — the three cases where "who did this, and can we
 * prove it" has to be answerable after the fact. Ordinary reads are not here;
 * an audit table that records everything is one nobody reads.
 */
public enum AuditAction {

    /** An admin enabled or disabled another account. */
    SET_ENABLED,

    /** An admin changed another account's role. */
    CHANGE_ROLE,

    /** An admin deleted another account. Irreversible. */
    DELETE_USER,

    /**
     * An admin cleared an account's lockout state ahead of the automatic
     * cooldown. The account-lockout counter is not visible or resettable any
     * other way: it is separate from {@code enabled}, and disabling then
     * re-enabling an account does not touch it.
     */
    UNLOCK_ACCOUNT,

    /**
     * An admin revealed another account's email address.
     *
     * <p>Recorded because the listing endpoint no longer returns email in bulk:
     * reading one is now a deliberate, purpose-stated act, and the record is
     * what makes that purpose limitation more than a comment.
     */
    READ_USER_EMAIL,

    /** A user exported their own personal data. */
    SELF_EXPORT,

    /** A user erased their own account. Irreversible. */
    SELF_ERASURE
}
