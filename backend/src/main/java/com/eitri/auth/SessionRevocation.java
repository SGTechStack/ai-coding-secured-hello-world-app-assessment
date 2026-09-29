package com.eitri.auth;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Ends every server-side session indexed under an account's principal name (its username). */
@Component
class SessionRevocation {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    SessionRevocation(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    void revokeAll(String username) {
        sessions.findByPrincipalName(username).keySet().forEach(sessions::deleteById);
    }
}
