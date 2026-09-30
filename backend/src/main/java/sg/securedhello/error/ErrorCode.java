package sg.securedhello.error;

import java.util.Locale;

/**
 * The closed error-code enum (ADR-031). Each code pairs with exactly one HTTP status and carries a constant
 * {@code title} and {@code detail}; clients branch on the code and on nothing else (REJ-092).
 *
 * <p>This enum is the source of the generated {@code docs/api/error-contract.md} and
 * {@code docs/api/error-contract.schema.json} (R-AUTH-003). Adding, removing or editing a code changes both, and the
 * drift gate fails {@code verify} until they are regenerated. Each code also has an {@link #category()} and a
 * {@link #followUpAction()}, which the application's ERROR line carries (T-AUD-002).
 */
public enum ErrorCode {

    AUTHENTICATION_FAILED(401, "Authentication failed",
            "Authentication is required, or the credentials were not accepted.",
            "every password-axis sign-in failure, an unauthenticated request, and absolute expiry"),
    PASSWORD_CHANGE_REQUIRED(403, "Password change required",
            "The password must be changed before this request can be made.",
            "a forced-change credential used outside the allowlist"),
    CSRF_TOKEN_INVALID(403, "CSRF token invalid",
            "The CSRF token is missing, invalid or no longer current.",
            "a missing, wrong or superseded CSRF token, including logout on a dead session"),
    ACCESS_DENIED(403, "Access denied",
            "The request is not permitted.",
            "insufficient role, an unmatched route for a signed-in caller, and a self-action refusal"),
    VALIDATION_FAILED(400, "Validation failed",
            "The request was not valid.",
            "format and length rejections, the body size cap, and rule USERNAME_UNAVAILABLE"),
    PASSWORD_REJECTED(400, "Password rejected",
            "The password does not meet the password policy.",
            "password policy, with a rule member"),
    RESET_TOKEN_INVALID(400, "Token invalid",
            "The token is invalid or has expired.",
            "any failed redemption of an activation or reset token"),
    USER_EXISTS(400, "User exists",
            "A user with that username or email address already exists.",
            "admin-initiated creation only, including tombstone hits"),
    TOO_MANY_REQUESTS(429, "Too many requests",
            "Too many requests. Try again later.",
            "every throttle and a tier-1 factor lock; always carries an integer Retry-After"),
    MISSING_FACTOR(412, "Second factor required",
            "A current second-factor verification is required.",
            "second factor absent or expired"),
    INVALID_FACTOR(412, "Second factor invalid",
            "The second-factor code was not accepted.",
            "wrong second-factor code"),
    FACTOR_ENROLMENT_REQUIRED(422, "Second factor enrolment required",
            "A second factor must be enrolled before this request can be made.",
            "an unenrolled admin on the admin surface"),
    FACTOR_ALREADY_ENROLLED(409, "Second factor already enrolled",
            "A second factor is already enrolled.",
            "provisioning when a confirmed factor exists"),
    FACTOR_DISABLED(423, "Second factor disabled",
            "The second factor is disabled. Contact an administrator.",
            "tier-2 disable, on the self-read, the admin entry point and verification"),
    TWO_ADMIN_INVARIANT(409, "Two-admin minimum",
            "The change would leave fewer than two enrolled administrators.",
            "the two-admin invariant's refusal of a disable, demote or delete"),
    INTERNAL_ERROR(500, "Internal error",
            "An unexpected error occurred.",
            "anything unhandled");

    /** The prefix of every {@link #type()}; the code in lower case, hyphenated, follows it. */
    public static final String TYPE_PREFIX = "tag:securedhello.sg,2026:problem:";

    private final int status;
    private final String title;
    private final String detail;
    private final String usage;

    ErrorCode(int status, String title, String detail, String usage) {
        this.status = status;
        this.title = title;
        this.detail = detail;
        this.usage = usage;
    }

    /** The one HTTP status this code is sent with. */
    public int status() {
        return status;
    }

    /** The constant RFC 9457 {@code title}. */
    public String title() {
        return title;
    }

    /** The constant RFC 9457 {@code detail}; never an exception message. */
    public String detail() {
        return detail;
    }

    /** What the code is used for, as documented in the generated error contract. */
    public String usage() {
        return usage;
    }

    /** The RFC 9457 {@code type}, derived from the code for conformance; clients never read it. */
    public String type() {
        return TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** {@code error.category}: the code's family, one of a closed set (R-AUD-039). */
    public ErrorCategory category() {
        return switch (this) {
            case VALIDATION_FAILED, PASSWORD_REJECTED, RESET_TOKEN_INVALID -> ErrorCategory.VALIDATION;
            case AUTHENTICATION_FAILED, MISSING_FACTOR, INVALID_FACTOR, FACTOR_ENROLMENT_REQUIRED, FACTOR_DISABLED ->
                    ErrorCategory.AUTHENTICATION;
            case PASSWORD_CHANGE_REQUIRED, CSRF_TOKEN_INVALID, ACCESS_DENIED -> ErrorCategory.AUTHORIZATION;
            case TOO_MANY_REQUESTS -> ErrorCategory.RATE_LIMIT;
            case USER_EXISTS, FACTOR_ALREADY_ENROLLED, TWO_ADMIN_INVARIANT -> ErrorCategory.CONFLICT;
            case INTERNAL_ERROR -> ErrorCategory.SERVER;
        };
    }

    /** {@code error.follow_up_action}: what the caller must do next, one of a closed set (R-AUD-039). */
    public FollowUpAction followUpAction() {
        return switch (this) {
            case TOO_MANY_REQUESTS -> FollowUpAction.RETRY_LATER;
            case AUTHENTICATION_FAILED, MISSING_FACTOR, INVALID_FACTOR -> FollowUpAction.RE_AUTHENTICATE;
            case ACCESS_DENIED, FACTOR_DISABLED, INTERNAL_ERROR -> FollowUpAction.CONTACT_ADMIN;
            case VALIDATION_FAILED, PASSWORD_REJECTED, RESET_TOKEN_INVALID, USER_EXISTS, PASSWORD_CHANGE_REQUIRED,
                    CSRF_TOKEN_INVALID, FACTOR_ENROLMENT_REQUIRED, FACTOR_ALREADY_ENROLLED, TWO_ADMIN_INVARIANT ->
                    FollowUpAction.NONE;
        };
    }

    /**
     * The code for a response whose producer knows only an HTTP status: a framework exception reaching the advice, or
     * the {@code /error} dispatch. A missing route or method is {@link #ACCESS_DENIED}, because every route without an
     * authorization-matrix row is denied anyway (ADR-043); anything else unrecognised is {@link #INTERNAL_ERROR}.
     */
    public static ErrorCode forStatus(int status) {
        return switch (status) {
            case 400, 406, 413, 415 -> VALIDATION_FAILED;
            case 401 -> AUTHENTICATION_FAILED;
            case 403, 404, 405 -> ACCESS_DENIED;
            case 429 -> TOO_MANY_REQUESTS;
            default -> INTERNAL_ERROR;
        };
    }
}
