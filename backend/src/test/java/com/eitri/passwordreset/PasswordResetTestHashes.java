package com.eitri.passwordreset;

/** Exposes the stored-token hash to tests in other packages that seed reset tokens directly. */
public final class PasswordResetTestHashes {

    private PasswordResetTestHashes() {}

    public static String hash(String token) {
        return PasswordResetService.hash(token);
    }
}
