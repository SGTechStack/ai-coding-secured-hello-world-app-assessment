package sg.example.helloauth.api;

/** The stable {@code code} values of Problem Details responses. Clients match on these strings. */
public enum ErrorCode {

    VALIDATION_FAILED("validation failed"),
    USER_EXIST("user exist"),
    INVALID_CREDENTIALS("invalid credentials"),
    UNAUTHENTICATED("unauthenticated"),
    FORBIDDEN("forbidden"),
    NOT_FOUND("not found"),
    TOO_MANY_REQUESTS("too many requests"),
    /** One answer for a token that is unknown, used or expired, so none can be told apart. */
    PASSWORD_RESET_TOKEN_INVALID("password reset token expired or invalid"),
    SELF_ACTION_NOT_ALLOWED("self action not allowed"),
    /** The HTTP reason phrase, as for any status without a code of its own. */
    SERVICE_UNAVAILABLE("service unavailable");

    private final String value;

    ErrorCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
