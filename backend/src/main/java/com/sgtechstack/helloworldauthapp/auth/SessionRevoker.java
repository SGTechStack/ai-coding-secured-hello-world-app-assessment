package com.sgtechstack.helloworldauthapp.auth;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Expires every active session belonging to one user.
 *
 * <p>Why this needs to exist: a user's authorities are resolved once, at
 * authentication time, and then cached in the session's {@code Authentication}.
 * Any change that is supposed to <em>reduce</em> what an account can do — a
 * password reset, a suspension, a demotion from ADMIN — therefore has no
 * effect at all on a session that already exists. Without revocation, a
 * suspended account keeps working until its session happens to expire, and a
 * demoted admin keeps admin authorities long enough to undo their own
 * demotion.
 *
 * <p>Extracted so there is exactly one implementation of this. It previously
 * lived as a private method inside the password-reset service, which meant the
 * reset path revoked sessions and the admin path silently did not.
 *
 * <p><strong>Scope limitation:</strong> {@link SessionRegistry} is backed by
 * in-process state, so this only reaches sessions held by the instance that
 * handles the call. A multi-instance deployment needs a shared session store
 * (Spring Session) before revocation can be relied on.
 */
@Component
public class SessionRevoker {

    private final SessionRegistry sessionRegistry;

    public SessionRevoker(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Expires all sessions held by the given user.
     *
     * <p>Expiry rather than outright removal is deliberate: it lets
     * {@code RestSessionExpiredStrategy} answer the user's next request with a
     * 401 instead of dropping the request on the floor.
     *
     * @return how many sessions were expired, so callers can record it
     */
    public int revokeAllSessionsFor(UUID userId) {
        List<SessionInformation> sessions = sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof UserPrincipal userPrincipal
                        && userPrincipal.getId().equals(userId))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .toList();

        sessions.forEach(SessionInformation::expireNow);

        return sessions.size();
    }
}
