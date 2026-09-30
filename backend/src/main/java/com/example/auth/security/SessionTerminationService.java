package com.example.auth.security;

import java.util.Collections;
import java.util.List;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User.UserBuilder;
import org.springframework.stereotype.Component;

/**
 * Force-expires every active session for a username via {@link SessionRegistry}. Shared by
 * {@code PasswordResetService} (Story 7: a reset invalidates every existing session) and {@code
 * AdminUserService} (an admin disabling, deleting or demoting a user must take effect
 * immediately, not just block that user's next login attempt).
 *
 * <p>{@link SessionRegistry} takes the {@code Authentication}'s principal object, not a username
 * -- the Spring Session-backed registry resolves that to its name and looks the sessions up by
 * the store's principal-name index. A throwaway {@code UserDetails} built with just the username
 * (the principal type {@code AppUserDetailsService} returns) therefore finds the real
 * principal's sessions correctly.
 *
 * <p>Callers must invoke this only after the triggering change has already committed (see the
 * password-reset and admin controllers) -- the session store writes in its own transaction, not
 * the caller's, so sweeping it before commit would let a session created in the gap between the
 * sweep and the actual commit survive undetected.
 */
@Component
public class SessionTerminationService {

    private final SessionRegistry sessionRegistry;

    public SessionTerminationService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void expireSessionsFor(String username) {
        UserBuilder principalLookupKey = org.springframework.security.core.userdetails.User.withUsername(username)
                .password("N/A")
                .authorities(Collections.emptyList());
        List<SessionInformation> sessions = sessionRegistry.getAllSessions(principalLookupKey.build(), false);
        sessions.forEach(SessionInformation::expireNow);
    }
}
