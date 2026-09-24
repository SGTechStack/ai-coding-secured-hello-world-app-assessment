package com.example.helloauth.admin;

/**
 * Admin-module rejections. Each subtype maps to a distinct HTTP status in
 * {@code ApiExceptionHandler} — self-targeting is a 400 domain rejection,
 * an unknown target id is 404.
 */
public abstract class AdminException extends RuntimeException {

    protected AdminException(String message) {
        super(message);
    }

    /**
     * The acting admin targeted their own account. All three mutation
     * endpoints reject self-targeting uniformly — an admin must never be
     * able to disable, demote, or delete themselves (that could lock out
     * the only admin).
     */
    public static class SelfAction extends AdminException {
        public SelfAction(String action) {
            super("Administrators cannot " + action + " their own account.");
        }
    }

    /** The target id does not name an existing account. */
    public static class UserNotFound extends AdminException {
        public UserNotFound(Long id) {
            super("No user with id " + id + ".");
        }
    }
}
