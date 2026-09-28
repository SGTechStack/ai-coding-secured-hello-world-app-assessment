package com.example.helloauth.service.exception;

/**
 * The failures the service layer can signal, kept together because they only make sense as a set.
 *
 * <p>None of them carries an HTTP status. Mapping to a status code is the web layer's job
 * ({@code ApiExceptionHandler}), which keeps the services free of a transport concern and keeps
 * the whole status-code contract readable in one file.
 */
public final class AuthExceptions {

    private AuthExceptions() {}

    /**
     * One exception for every way a login can fail: unknown username, wrong password, locked
     * account, disabled account. That is not laziness — it is the PRD's enumeration-resistance
     * requirement expressed in the type system. A distinct "account locked" or "no such user"
     * exception invites a distinct response, which would leak whether the account exists.
     */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid credentials");
        }
    }

    /** Per-IP limit exceeded. Independent of any single account's lockout state. */
    public static class TooManyRequestsException extends RuntimeException {
        public TooManyRequestsException() {
            super("Too many attempts");
        }
    }

    /**
     * A username or email is already taken.
     *
     * <p>Registration deliberately does leak existence here, because the PRD asks for it: "rejected
     * with a clear validation error (username/email conflict)". A visitor cannot be asked to pick a
     * unique name without being told when they have not. Login and password reset, where no such
     * necessity exists, stay silent.
     */
    public static class RegistrationConflictException extends RuntimeException {
        private final String field;

        public RegistrationConflictException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }

    /** The supplied password fails the length policy. */
    public static class WeakPasswordException extends RuntimeException {
        public WeakPasswordException(String message) {
            super(message);
        }
    }

    /**
     * A reset token that is unknown, expired, or already redeemed. Again one exception for all
     * three, so the response cannot be used to probe which tokens exist.
     */
    public static class InvalidResetTokenException extends RuntimeException {
        public InvalidResetTokenException() {
            super("Invalid or expired reset token");
        }
    }

    /** An admin operation named an account that does not exist. */
    public static class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException() {
            super("Account not found");
        }
    }

    /**
     * An admin aimed a disable, re-role or delete at their own account.
     *
     * <p>Distinct from a plain authorization failure on purpose: the caller *is* an admin, so
     * answering 403 would be indistinguishable from "you are not an admin" and the frontend could
     * not explain what actually happened.
     */
    public static class SelfActionForbiddenException extends RuntimeException {
        public SelfActionForbiddenException(String message) {
            super(message);
        }
    }
}
