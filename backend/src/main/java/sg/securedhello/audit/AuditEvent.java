package sg.securedhello.audit;

import static sg.securedhello.audit.AuditKey.ACTIVE_PROFILES;
import static sg.securedhello.audit.AuditKey.AUDIT_LOGGERS;
import static sg.securedhello.audit.AuditKey.ENROLLED_ADMINS_AFTER;
import static sg.securedhello.audit.AuditKey.EVENTS_UNTRACKED_COUNT;
import static sg.securedhello.audit.AuditKey.HOST_IP;
import static sg.securedhello.audit.AuditKey.HOST_NAME;
import static sg.securedhello.audit.AuditKey.IPV6_PREFIX_LENGTH;
import static sg.securedhello.audit.AuditKey.KEY_FINGERPRINTS;
import static sg.securedhello.audit.AuditKey.PROCESS_REAL_USER;
import static sg.securedhello.audit.AuditKey.RECONCILED_ACCOUNTS;
import static sg.securedhello.audit.AuditKey.RUNNER_REASON;
import static sg.securedhello.audit.AuditKey.SESSIONS_ENDED_COUNT;
import static sg.securedhello.audit.AuditKey.SOURCE_DISTINCT_COUNT;
import static sg.securedhello.audit.AuditKey.TRUNCATED_ROWS;
import static sg.securedhello.audit.AuditKey.USER_DISTINCT_COUNT;
import static sg.securedhello.audit.AuditKey.USER_ID;
import static sg.securedhello.audit.AuditKey.USER_TARGET_COUNT;
import static sg.securedhello.audit.AuditKey.USER_TARGET_ID;
import static sg.securedhello.audit.AuditKey.USER_TARGET_UNLOCK_REASON;
import static sg.securedhello.audit.AuditRowDefinition.row;

import org.slf4j.event.Level;

import sg.securedhello.audit.AuditRowDefinition.Keying;
import sg.securedhello.audit.AuditRowDefinition.Outcome;
import sg.securedhello.audit.AuditRowDefinition.Scope;
import sg.securedhello.audit.AuditRowDefinition.Severity;

/**
 * The audit catalogue: one member per audit row, and the only list of what the audit stream can contain (ADR-055).
 * {@link AuditEmitter#emit} writes every row from its member's constants plus a typed {@link AuditContext}. The ASVS
 * 16.1.1 log inventory, {@code docs/audit/log-inventory.md}, is generated from this enum and checked for drift
 * (T-AUD-017).
 *
 * <h2>Adding an event</h2>
 * A later ticket adds a row with one constant here and, if the row carries keys, one context record:
 * <ol>
 *   <li>If the row carries a reason, write its family: an enum implementing {@link AuditReason} whose constants return
 *       pinned {@code code()} values, named in {@code AuditReason}'s {@code permits} and in the T-AUD-045 list.</li>
 *   <li>Add any new key to {@link AuditKey}, by its ECS field name.</li>
 *   <li>Write the context: a record of UUIDs, enums and numbers that implements {@link AuditContext} and writes its
 *       keys, and its reason typed as the family, so a reason from another family does not compile. For example:
 *       <pre>{@code
 * record CsrfRejection(UUID userId, CsrfReason reason) implements AuditContext {
 *     public void writeTo(AuditFields fields) {
 *         fields.put(AuditKey.USER_ID, userId).reason(reason);
 *     }
 * }}</pre></li>
 *   <li>Add the constant: {@code row(action, message)} with its type, outcome, level and severity, its reason family
 *       and its required and optional keys. Rows are request-scoped unless declared {@code PROCESS}.</li>
 *   <li>If anyone can trigger the row without an account, key it (ADR-019): {@code .keyed(Keying.SOURCE)} when its
 *       key space is the source, {@code .keyed(Keying.USER)} when it needs a resolved user. Otherwise it would reopen
 *       the audit-volume arithmetic (R-AUD-031).</li>
 *   <li>Regenerate the inventory with {@code -Daudit-inventory.regenerate=true} and commit it.</li>
 * </ol>
 * Nothing else changes: the emitter validates the keys, derives the request fields and writes the row.
 *
 * <h2>The action</h2>
 * {@code event.action} is a value of {@code Log_Schema.md}'s closed enum, never a new one (R-STD-004; T-AUD-046): the
 * message and {@code event.type} tell rows of one action apart. A TOTP verification, and its factor's lock and
 * disable, are {@code user-authentication}, like the password's; a reset request is {@code password-reset}; the
 * startup reconciliation, which ends sessions, is {@code session-end}; and a TOTP context mismatch, a ciphertext
 * presented as another account's, is {@code access-control}, since the key is not a KMS key ({@code KMS_DECRYPT}
 * would misname it; ADR-022).
 */
public enum AuditEvent {

    /** Row 1: a password sign-in succeeded. Written after the session id rotated, so it carries the new hash. */
    LOGIN_SUCCESS(row("user-authentication", "Login succeeded.")
            .type("user")
            .required(USER_ID)
            .build()),

    /**
     * Row 2: a password sign-in failed. The wire answer is the uniform 401 (ADR-033); the reason is here only.
     * {@code user.id} is written when the account resolves and omitted when it does not (REJ-042; R-AUD-007).
     */
    LOGIN_FAILURE(row("user-authentication", "Login failed.")
            .type("user")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .reasons(LoginFailureReason.class)
            .optional(USER_ID)
            .build()),

    /**
     * Row 3: an account was locked by consecutive password failures (ADR-011; ADR-012). WARN, not ERROR: the
     * catalogue fixes the level (REJ-043). Not keyed: lockout transitions are tier 3, and an account locks at most
     * once a rung (ADR-019).
     */
    LOCKOUT_TRIGGERED(row("user-authentication", "Account locked.")
            .type("error")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.HIGH)
            .reasons(LockoutReason.class)
            .required(USER_ID)
            .build()),

    /**
     * Row 4: a password lock was cleared. The lift is lazy, with no scheduler, so it is observed where the lock is
     * cleared: the next sign-in that finds it lifted, or a reset redemption that clears it (ADR-009). An admin unlock
     * writes row 32 instead, which names the administrator and carries their reason.
     */
    LOCKOUT_CLEARED(row("user-authentication", "Account lock cleared.")
            .type("change")
            .reasons(LockoutClearReason.class)
            .required(USER_ID)
            .build()),

    /**
     * Row 45: an account's password failures since its last success reached the alert threshold, the warning about
     * 580 minutes before the NIST cap disables its password (ADR-011; ADR-013). Once per transition; an alert.
     */
    PASSWORD_FAILURE_ALERT(row("user-authentication", "Password failures reached the alert threshold.")
            .type("error")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.HIGH)
            .required(USER_ID)
            .build()),

    /**
     * The NIST cap disabled an account's password authenticator; only rebinding clears it (ADR-013; R-LCK-004). ERROR
     * and critical, like the factor's tier-2 disable. The reason tells the automatic trip from an operator action.
     */
    PASSWORD_DISABLED(row("user-authentication", "Password disabled.")
            .type("error")
            .outcome(Outcome.FAILURE)
            .level(Level.ERROR, Severity.CRITICAL)
            .reasons(PasswordDisableReason.class)
            .required(USER_ID)
            .build()),

    /**
     * Row 5: a request was throttled on its source key, by a budget-table row or by the session-miss budget. A tier-1
     * keyed row: one per source, reason and keying window, with its count (ADR-019).
     */
    SOURCE_THROTTLED(row("access-control", "Request throttled for its source.")
            .type("denied")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .reasons(SourceThrottleReason.class)
            .keyed(Keying.SOURCE)
            .build()),

    /**
     * Row 6: a request was throttled on the value it submitted, such as a login username. Transition-keyed: written
     * once when the value's bucket runs out, not for each refusal until it is admitted again. It carries no identity
     * by construction, since the value may name no account and its bucket must not become an existence oracle.
     */
    IDENTIFIER_THROTTLED(row("access-control", "Request throttled for its submitted identifier.")
            .type("denied")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .reasons(IdentifierThrottleReason.class)
            .build()),

    /**
     * A password reset was requested (PRD Story 6). It carries no {@code user.id}, and is written whether or not the
     * address names an account, so the row never confirms one exists (REJ-002). Anyone can trigger it, so it is a
     * tier-1 keyed row: one per source and keying window, with its count (ADR-019).
     */
    PASSWORD_RESET_REQUESTED(row("password-reset", "Password reset requested.")
            .keyed(Keying.SOURCE)
            .build()),

    /**
     * A password-reset token was redeemed and the account's password set (PRD Story 7), whether the user or an
     * administrator issued the token. It names the account (REJ-002); only a valid token reaches it.
     */
    PASSWORD_RESET_COMPLETED(row("password-reset", "Password reset completed.")
            .type("change")
            .required(USER_ID)
            .build()),

    /**
     * Row 16: a self-registration was accepted (ADR-032). The wire is the same 202 whatever the address's state; the row
     * is specific: {@code NEW_ACCOUNT} names the new pending registration, {@code EXISTING_ADDRESS} names nothing.
     * Written after the reservation commits. Per event: the registration route is budgeted (ADR-019).
     */
    REGISTRATION_ACCEPTED(row("user-provisioning", "Registration accepted.")
            .type("creation")
            .reasons(RegistrationReason.class)
            .optional(USER_ID)
            .build()),

    /**
     * Row 17: a self-registration was refused because its username is unavailable (400 {@code VALIDATION_FAILED} with
     * rule {@code USERNAME_UNAVAILABLE}). It names no account. Per event: the registration route is budgeted.
     */
    REGISTRATION_REFUSED(row("user-provisioning", "Registration refused.")
            .type("creation")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.LOW)
            .reasons(RegistrationRefusalReason.class)
            .build()),

    /**
     * Row 20: a submitted activation or reset token did not redeem (400 {@code RESET_TOKEN_INVALID}). It names no
     * account, and is written after the redemption's transaction rolled back. Per event: both redemption routes are
     * budgeted.
     */
    CREDENTIAL_TOKEN_REFUSED(row("password-reset", "Credential token redemption failed.")
            .type("change")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .reasons(TokenRedemptionFailureReason.class)
            .build()),

    /** Row 7: a signed-in user signed out. */
    LOGOUT(row("user-logout", "Logout succeeded.")
            .type("end")
            .required(USER_ID)
            .build()),

    /**
     * Row 8: the session id is about to rotate at authentication. Written before the rotation, so its
     * {@code session.hash} is the pre-login one that joins the attempts before it to the sign-in (ADR-038).
     */
    SESSION_START(row("session-start", "Session started.")
            .type("start")
            .reasons(SessionStartReason.class)
            .required(USER_ID)
            .build()),

    /**
     * Row 9: a signed-in session reached its absolute lifetime, measured from sign-in, and the absolute-lifetime filter
     * ended it (ADR-038). Written before the session is invalidated, so it carries that session's hash. Per event: at
     * most once per sign-in.
     */
    SESSION_EXPIRED(row("session-end", "Session ended at its absolute lifetime.")
            .type("end")
            .reasons(SessionTimeoutReason.class)
            .required(USER_ID)
            .build()),

    /**
     * Row 10: a newer sign-in displaced a session of the same account (one session per account; R-AUTH-002). Written
     * at displacement, inside the login composite, before the session-start row; {@code user.id} is the displaced
     * account, and the request fields are the new sign-in's. Per event: behind the login budget.
     */
    SESSION_EVICTED(row("session-end", "Session ended by a newer sign-in.")
            .type("end")
            .reasons(SessionEvictionReason.class)
            .required(USER_ID)
            .build()),

    /**
     * Row 11: a request presented a session id that resolved to no live session, or more than one session cookie
     * (R-SES-007). Idle expiry is observed only here, lazily, and cannot be told from a forged or deleted id
     * (R-AUD-003). It carries no identity, since no session resolved. Anyone can send a cookie, so it is a tier-1 keyed
     * row (ADR-019).
     */
    SESSION_INVALID(row("session-end", "Invalid session presented.")
            .type("end")
            .outcome(Outcome.FAILURE)
            .reasons(InvalidSessionReason.class)
            .keyed(Keying.SOURCE)
            .build()),

    /**
     * Row 12: a signed-in caller was refused 403 {@code ACCESS_DENIED}: a role that does not grant the route, or a
     * route denied to everyone (ADR-043). A self-action refusal writes row 34 instead. Its key space is the user
     * population, so it is a tier-2 keyed row (ADR-019).
     */
    ACCESS_DENIED(row("access-control", "Access denied.")
            .type("denied")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .reasons(AccessDeniedReason.class)
            .required(USER_ID)
            .keyed(Keying.USER)
            .build()),

    /**
     * Row 14: the admin surface answered a signed-in administrator 412 {@code MISSING_FACTOR}: the session holds no
     * TOTP factor, or holds an expired one (ADR-021; ADR-026). A tier-2 keyed row (ADR-019).
     */
    FACTOR_REQUIRED(row("access-control", "Second factor required.")
            .type("denied")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .reasons(FactorRequiredReason.class)
            .required(USER_ID)
            .keyed(Keying.USER)
            .build()),

    /**
     * Row 35: a signed-in user read their own profile ({@code GET /api/profile}). Every page load makes one, so it is a
     * tier-2 keyed row rather than no row at all: the event and its magnitude remain (REJ-082).
     */
    PROFILE_READ(row("profile-read", "Profile read.")
            .type("allowed")
            .required(USER_ID)
            .keyed(Keying.USER)
            .build()),

    /**
     * Row 13: a CSRF check refused an unsafe request. {@code user.id} only when the caller is signed in. A tier-1 keyed
     * row, because any unsafe request from anywhere can produce it (ADR-019).
     */
    CSRF_REJECTED(row("access-control", "CSRF validation failed.")
            .type("denied")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .reasons(CsrfReason.class)
            .optional(USER_ID)
            .keyed(Keying.SOURCE)
            .build()),

    /**
     * Row 36: an administrator provisioned a TOTP secret, writing a pending enrolment (ADR-025). The row carries no
     * secret, URI or ciphertext (T-AUD-021).
     */
    TOTP_ENROLMENT_PROVISIONED(row("totp-enrol", "TOTP enrolment provisioned.")
            .type("creation")
            .required(USER_ID)
            .build()),

    /** Row 37: a code confirmed the pending secret, which is now the account's factor (enrolment binding; REJ-071). */
    TOTP_ENROLMENT_CONFIRMED(row("totp-enrol", "TOTP enrolment confirmed.")
            .type("change")
            .required(USER_ID)
            .build()),

    /**
     * A code did not confirm the pending secret, or there was no pending enrolment to confirm. Nothing changed and the
     * pending row stays for a retry (T-MFA-016).
     */
    TOTP_ENROLMENT_FAILED(row("totp-enrol", "TOTP enrolment confirmation failed.")
            .type("change")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .required(USER_ID)
            .build()),

    /** Row 38: a code verified against the enrolled factor, which was granted or renewed to the session (ADR-021). */
    TOTP_VERIFIED(row("user-authentication", "TOTP verification succeeded.")
            .type("user")
            .required(USER_ID)
            .build()),

    /**
     * Row 39: a code did not verify against the enrolled factor: wrong, outside the window, or a replay (R-MFA-017).
     * Every guess costs the password, so the row always names the account (ADR-027).
     */
    TOTP_VERIFICATION_FAILED(row("user-authentication", "TOTP verification failed.")
            .type("user")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .required(USER_ID)
            .build()),

    /**
     * Row 40: consecutive wrong codes locked an administrator's factor for 20 minutes (tier 1; ADR-027). WARN, once
     * per transition; the lock lifts by itself.
     */
    TOTP_FACTOR_LOCKED(row("user-authentication", "TOTP factor locked.")
            .type("error")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.HIGH)
            .required(USER_ID)
            .build()),

    /**
     * Row 41: cumulative wrong codes disabled an administrator's factor (tier 2; ADR-027), which also forced a
     * password change and ended the account's sessions. ERROR and critical, like the password's disable: an automatic
     * trip, which only rebinding clears, and one trigger of the break-glass runner.
     */
    TOTP_FACTOR_DISABLED(row("user-authentication", "TOTP factor disabled.")
            .type("error")
            .outcome(Outcome.FAILURE)
            .level(Level.ERROR, Severity.CRITICAL)
            .required(USER_ID)
            .build()),

    /**
     * Row 33: an administrator reset another account's TOTP factor, deleting its confirmed and pending rows and, after
     * commit, its sessions (ADR-049). With the admin password reset's row, the detector of one admin taking another
     * over (R-ADM-009).
     */
    TOTP_REMOVED(row("totp-remove", "TOTP factor reset.")
            .type("change")
            .required(USER_ID, USER_TARGET_ID)
            .build()),

    /**
     * An administrator read the user list; the row carries how many accounts it returned (PRD Story 8). Log_Schema's
     * closed {@code event.action} has no read value for the admin surface, so it shares the admin actions' value and
     * {@code event.type} {@code access} tells it apart (R-STD-004).
     */
    ADMIN_USERS_LISTED(row("user-administration", "Administrator listed users.")
            .type("access")
            .required(USER_ID, USER_TARGET_COUNT)
            .build()),

    /** An administrator read one account; the row names it by UUID only (R-STD-008). */
    ADMIN_USER_VIEWED(row("user-administration", "Administrator read a user.")
            .type("access")
            .required(USER_ID, USER_TARGET_ID)
            .build()),

    /**
     * Row 28: an administrator enabled an account; a re-enable also issued its forced-change credential (ADR-046).
     * Log_Schema's closed {@code event.action} has no value of its own for it, so the message tells it apart (R-STD-004).
     */
    ADMIN_USER_ENABLED(row("user-administration", "Account enabled.")
            .type("change")
            .required(USER_ID, USER_TARGET_ID)
            .build()),

    /** Row 29: an administrator disabled an account, whose sessions end after commit (ADR-037). */
    ADMIN_USER_DISABLED(row("user-administration", "Account disabled.")
            .type("change")
            .required(USER_ID, USER_TARGET_ID)
            .build()),

    /** An administrator changed an account's role to {@code ADMIN}; its sessions end after commit (ADR-037). */
    ADMIN_USER_PROMOTED(row("user-administration", "Account role changed to administrator.")
            .type("change")
            .required(USER_ID, USER_TARGET_ID)
            .build()),

    /** An administrator changed an account's role to {@code USER}; its sessions end after commit (ADR-037). */
    ADMIN_USER_DEMOTED(row("user-administration", "Account role changed to user.")
            .type("change")
            .required(USER_ID, USER_TARGET_ID)
            .build()),

    /**
     * An administrator deleted an account and left its tombstone (ADR-044); its sessions end after commit. The row
     * names the account by UUID only, the tombstone's {@code user_id}.
     */
    ADMIN_USER_DELETED(row("user-administration", "Account deleted.")
            .type("deletion")
            .required(USER_ID, USER_TARGET_ID)
            .build()),

    /**
     * Row 34: {@code AdminActionGuard} refused an admin mutation, as a self-action or under the two-admin invariant
     * (ADR-048), or its lock set timed out under contention, so the guard could not run. One row per refused attempt;
     * nothing changed.
     */
    ADMIN_ACTION_REFUSED(row("user-administration", "Administrative action refused.")
            .type("error")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .reasons(AdminRefusalReason.class)
            .required(USER_ID, USER_TARGET_ID)
            .build()),

    /**
     * Row 27: an administrator invited an account, a pending registration whose activation token was returned to them
     * once (ADR-006). The row never carries the token.
     */
    ADMIN_USER_INVITED(row("user-provisioning", "Account invited.")
            .type("creation")
            .required(USER_ID, USER_TARGET_ID)
            .build()),

    /**
     * Row 27, re-issued: an administrator invited an account that was already their pending invite, so its outstanding
     * activation token was cancelled and a new one returned once (ADR-007 amendment). The message tells it apart from
     * a first invite (R-STD-004). The row never carries the token.
     */
    ADMIN_USER_REINVITED(row("user-provisioning", "Invitation re-issued.")
            .type("change")
            .required(USER_ID, USER_TARGET_ID)
            .build()),

    /**
     * Row 18, admin issuance: an administrator was returned a password-reset token for an account, whose sessions end
     * after commit (ADR-006; ADR-037). It clears no lock (REJ-016). The row never carries the token. It is the detector
     * of one admin taking over another (R-ADM-009).
     */
    ADMIN_RESET_ISSUED(row("password-reset", "Password reset token issued by an administrator.")
            .type("change")
            .required(USER_ID, USER_TARGET_ID)
            .build()),

    /**
     * Row 32: an administrator unlocked an account, clearing its password lockout and its tier-1 factor lock, never
     * tier 2 (REJ-072), for a closed reason in {@code user.target.unlock_reason} (REJ-028).
     */
    ADMIN_USER_UNLOCKED(row("user-administration", "Account unlocked.")
            .type("change")
            .required(USER_ID, USER_TARGET_ID, USER_TARGET_UNLOCK_REASON)
            .build()),

    /**
     * Row 42: a TOTP envelope's context prefix did not match the row it was read from, so ciphertext was moved between
     * users or replayed across key versions. A data-integrity alarm, not a decrypt error (ADR-028; R-MFA-020).
     */
    TOTP_CONTEXT_MISMATCH(row("access-control", "TOTP secret context mismatch.")
            .type("error")
            .outcome(Outcome.FAILURE)
            .level(Level.ERROR, Severity.CRITICAL)
            .required(USER_ID)
            .build()),

    /** Row 43: the application is ready. Records what later correlation depends on (LOG §3.1; ADR-054; ADR-057). */
    APPLICATION_STARTUP(row("application-startup", "Application started.")
            .type("start")
            .scope(Scope.PROCESS)
            .required(HOST_NAME, HOST_IP, ACTIVE_PROFILES, IPV6_PREFIX_LENGTH, KEY_FINGERPRINTS, AUDIT_LOGGERS)
            .build()),

    /**
     * The startup reconciliation sweep ran, before traffic was served: how many sessions it ended, and for how many
     * accounts under each durable-state trigger (ADR-039; R-SES-012). Written on every start, zeros included. A
     * non-zero count means a session kill was lost between a commit and its dispatch; it also counts an expired
     * session the cleanup job had not yet removed. Written during refresh, before the port opens, so it precedes the
     * startup row (row 43).
     */
    SESSIONS_RECONCILED(row("session-end", "Sessions reconciled at startup.")
            .type("end")
            .scope(Scope.PROCESS)
            .required(SESSIONS_ENDED_COUNT, RECONCILED_ACCOUNTS)
            .build()),

    /**
     * Row 46: a keying tier reached its distinct-key cap in a keying window. One per tier and window, written as the
     * window closes, with the exact tracked-key and untracked-occurrence counts and the rows truncated (ADR-019;
     * REJ-079). A per-event alert: reaching it means a campaign large enough to threaten the audit file (R-OBS-013).
     */
    KEYED_ROWS_TRUNCATED(row("access-control", "Keyed audit rows truncated.")
            .type("denied")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.HIGH)
            .scope(Scope.PROCESS)
            .reasons(TruncationReason.class)
            .required(EVENTS_UNTRACKED_COUNT, TRUNCATED_ROWS)
            .optional(SOURCE_DISTINCT_COUNT, USER_DISTINCT_COUNT)
            .build()),

    /**
     * Row 47: a shed episode ended, so new anonymous sessions are created again (ADR-041). Paired with the row 5
     * {@code DISK_RESERVE_SHED} that started it; a restart during an episode leaves it without this row (R-RL-010).
     */
    SHED_EPISODE_CLEARED(row("access-control", "Anonymous-session shedding cleared.")
            .type("change")
            .scope(Scope.PROCESS)
            .build()),

    /**
     * Row 48: a self-registered pending registration had lapsed, 24 hours after its last registration, and the next
     * registration of its username from another address deleted it, freeing the name (ADR-032 amendment). No tombstone
     * is written: it never held a credential. {@code user.id} is the deleted account; the registrant is anonymous. Per
     * event: the registration route is budgeted, and each row consumes a pending registration a day old (ADR-019).
     */
    PENDING_REGISTRATION_LAPSED(row("user-provisioning", "Lapsed pending registration deleted.")
            .type("deletion")
            .required(USER_ID)
            .build()),

    /**
     * The recovery runner's dry run (ADR-074; REJ-090): the digest it printed, over the state it would change. Nothing
     * changed. A separate row, so a preview by one operator and an apply by another is visible.
     */
    RECOVERY_PLANNED(row("user-administration", "Recovery runner dry run.")
            .type("info")
            .level(Level.WARN, Severity.MEDIUM)
            .scope(Scope.PROCESS)
            .required(RecoveryRunContext.COMMON_KEYS)
            .optional(PROCESS_REAL_USER, USER_TARGET_ID)
            .build()),

    /**
     * The recovery runner's intent row (REJ-090; R-RUN-010): its checks passed and its transaction is about to open.
     * It asserts no change, so its outcome is {@code unknown}; one with no outcome row after it is a run that died.
     */
    RECOVERY_STARTED(row("user-administration", "Recovery runner apply started.")
            .type("change")
            .outcome(Outcome.UNKNOWN)
            .level(Level.WARN, Severity.HIGH)
            .scope(Scope.PROCESS)
            .required(RecoveryRunContext.COMMON_KEYS).required(RUNNER_REASON)
            .optional(PROCESS_REAL_USER, USER_TARGET_ID)
            .build()),

    /**
     * The recovery runner's outcome row after commit (REJ-090): a break-glass change, outside {@code AdminActionGuard},
     * so it carries the enrolled-admin count before and after; a transition to zero is the alert (ADR-072; ADR-048).
     */
    RECOVERY_COMPLETED(row("user-administration", "Recovery runner apply completed.")
            .type("change")
            .level(Level.WARN, Severity.CRITICAL)
            .scope(Scope.PROCESS)
            .required(RecoveryRunContext.COMMON_KEYS).required(RUNNER_REASON, ENROLLED_ADMINS_AFTER)
            .optional(PROCESS_REAL_USER, USER_TARGET_ID)
            .build()),

    /** The recovery runner's outcome row after rollback (REJ-090): nothing changed. */
    RECOVERY_FAILED(row("user-administration", "Recovery runner apply failed.")
            .type("change")
            .outcome(Outcome.FAILURE)
            .level(Level.ERROR, Severity.HIGH)
            .scope(Scope.PROCESS)
            .required(RecoveryRunContext.COMMON_KEYS).required(RUNNER_REASON, ENROLLED_ADMINS_AFTER)
            .optional(PROCESS_REAL_USER, USER_TARGET_ID)
            .build()),

    /** Row 44: the application context is closing. */
    APPLICATION_SHUTDOWN(row("application-shutdown", "Application stopping.")
            .type("end")
            .scope(Scope.PROCESS)
            .build());

    private final AuditRowDefinition definition;

    AuditEvent(AuditRowDefinition definition) {
        this.definition = definition;
    }

    /** The row's constants. */
    public AuditRowDefinition definition() {
        return definition;
    }
}
