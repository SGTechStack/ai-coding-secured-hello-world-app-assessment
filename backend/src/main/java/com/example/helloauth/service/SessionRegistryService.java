package com.example.helloauth.service;

import com.example.helloauth.security.AppUserDetails;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Per-user session invalidation built on Spring Security's SessionRegistry. Expiring a
 * SessionInformation causes the session to be invalidated on its owner's next request
 * (and the underlying HttpSession is removed via the session-management filter).
 */
@Service
public class SessionRegistryService {

    private final SessionRegistry sessionRegistry;

    public SessionRegistryService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void invalidateSessionsForUser(UUID userId) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof AppUserDetails details && details.getId().equals(userId)) {
                List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
                for (SessionInformation info : sessions) {
                    // Mark expired; the ConcurrentSessionFilter rejects the session (401) on its
                    // owner's next request and removes it. Do NOT remove it here, or the filter
                    // would not see the expiry and would let the stale session through.
                    info.expireNow();
                }
            }
        }
    }
}
