package sg.securedhello.error;

import java.util.Locale;

/**
 * {@code error.category} on an application ERROR line: a closed set, derived from the {@link ErrorCode} family
 * ({@link ErrorCode#category()}). {@code Log_Schema.md} names the field but is not in this repository, so the values
 * are defined here and in the spec's error contract (R-AUD-039).
 */
public enum ErrorCategory {

    /** The request was malformed or refused by a policy on its content. */
    VALIDATION,
    /** The caller is not authenticated, or a factor is missing, wrong or disabled. */
    AUTHENTICATION,
    /** The caller is authenticated but not permitted. */
    AUTHORIZATION,
    /** A throttle refused the request. */
    RATE_LIMIT,
    /** The request conflicts with the current state. */
    CONFLICT,
    /** The application failed. */
    SERVER;

    /** The value written: lower case, hyphenated. */
    public String value() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
