package com.example.hello.auth;

import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Component;

@Component
public class SessionRevocation {
    private final JdbcIndexedSessionRepository sessions;
    public SessionRevocation(JdbcIndexedSessionRepository sessions) { this.sessions = sessions; }
    public void revoke(String username) {
        sessions.findByPrincipalName(username).keySet().forEach(sessions::deleteById);
    }
}
