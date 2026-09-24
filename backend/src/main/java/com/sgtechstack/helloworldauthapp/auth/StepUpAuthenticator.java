package com.sgtechstack.helloworldauthapp.auth;

import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Re-proves the password behind an existing session, immediately before an
 * irreversible action.
 *
 * <h2>What a session cookie does and does not prove</h2>
 *
 * A valid session proves somebody authenticated as this account at some point
 * in the last eight hours. It does not prove the person issuing <em>this</em>
 * request is that somebody. An unlocked laptop, a borrowed browser profile or a
 * cookie lifted before the {@code Secure}/{@code SameSite} hardening landed all
 * produce a perfectly valid session held by the wrong person.
 *
 * <p>For most requests that gap is acceptable — the session is the design, and
 * re-prompting constantly would train people to type their password into
 * anything that asks. For actions that cannot be undone it is not: deleting an
 * account destroys data and the access to it, and there is no state to restore.
 * Requiring the password at that moment narrows the window from "the session
 * lifetime" to "this request", which is the difference between an attacker
 * needing a cookie and needing the credential.
 *
 * <h2>The header, and its cost</h2>
 *
 * The password arrives in {@code X-Confirm-Password}. That is the same exposure
 * as the login form field — a request body or header over TLS — with one extra
 * hazard worth naming: headers are what proxies and gateways log by default,
 * where bodies are not. So the header must never be added to any access-log
 * format, and nothing in this application logs request headers.
 *
 * <p>A blank or missing value is rejected with the same exception as a wrong
 * one, so the response cannot be used to work out whether the client is
 * expected to send it.
 *
 * <p>The hash is re-read from the database rather than taken from the cached
 * principal, so a password changed mid-session takes effect here immediately
 * — and a session that outlived a password reset cannot confirm with the old
 * one.
 */
@Component
public class StepUpAuthenticator {

    /** Header carrying the re-entered password. */
    public static final String CONFIRM_PASSWORD_HEADER = "X-Confirm-Password";

    private static final String MESSAGE =
            "Re-enter your password to confirm this action";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public StepUpAuthenticator(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Verifies {@code submittedPassword} against the current stored hash for
     * {@code actorId}.
     *
     * @throws ReauthenticationRequiredException if the password is absent,
     *                                           blank or wrong
     */
    public void requirePassword(UUID actorId, String submittedPassword) {
        if (submittedPassword == null || submittedPassword.isBlank()) {
            throw new ReauthenticationRequiredException(MESSAGE);
        }

        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ReauthenticationRequiredException(MESSAGE));

        if (!passwordEncoder.matches(submittedPassword, actor.getPasswordHash())) {
            throw new ReauthenticationRequiredException(MESSAGE);
        }
    }
}
