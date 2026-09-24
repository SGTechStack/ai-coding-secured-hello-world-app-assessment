package com.example.helloauth.auth;

/**
 * Thrown when a source IP exhausts one of its per-IP budgets — mapped to
 * {@code 429 Too Many Requests} by {@link ApiExceptionHandler}. Covers both
 * of {@link IpThrottleService}'s buckets: the login-failure budget
 * ({@link LoginService}) and, since security-review F-04, the anonymous-
 * request budget on the other unauthenticated auth mutations (register,
 * password-reset request).
 *
 * <p>Deliberately <em>not</em> an {@code AuthenticationException}: the
 * controller maps every {@code AuthenticationException} to the generic 401
 * (enumeration resistance), while throttling is a rate-limit contract the SPA
 * is allowed to see distinctly.
 */
public class LoginThrottledException extends RuntimeException {

    public LoginThrottledException() {
        super("Too many failed login attempts. Try again later.");
    }

    /**
     * 429 body text for a non-login anonymous endpoint — the no-arg
     * message's "failed login attempts" wording would misdescribe a
     * throttled register/reset hit.
     */
    public LoginThrottledException(String detail) {
        super(detail);
    }
}
