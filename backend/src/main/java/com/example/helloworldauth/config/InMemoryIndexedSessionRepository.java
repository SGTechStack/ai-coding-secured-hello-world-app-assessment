package com.example.helloworldauth.config;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.PrincipalNameIndexResolver;
import org.springframework.session.Session;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory {@link FindByIndexNameSessionRepository} for the demo. Backed by a
 * plain map, like {@code MapSessionRepository}, but additionally implements the
 * indexed lookup so a user's sessions can be found by principal name.
 *
 * <p>The principal-name index is not maintained as a separate structure;
 * instead {@link #findByIndexNameAndIndexValue} resolves each stored session's
 * index value on demand with {@link PrincipalNameIndexResolver}, which reads the
 * {@code SPRING_SECURITY_CONTEXT} attribute a logged-in session carries. This is
 * enough for {@code findByPrincipalName(username)} and keeps the store simple;
 * for a large session count a maintained reverse index would be preferable.
 */
public class InMemoryIndexedSessionRepository
    implements FindByIndexNameSessionRepository<MapSession> {

    private final Map<String, Session> sessions;
    private final PrincipalNameIndexResolver<MapSession> indexResolver =
        new PrincipalNameIndexResolver<>();
    private Duration defaultMaxInactiveInterval = Duration.ofMinutes(30);

    public InMemoryIndexedSessionRepository(Map<String, Session> sessions) {
        this.sessions = sessions;
    }

    public void setDefaultMaxInactiveInterval(Duration defaultMaxInactiveInterval) {
        this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
    }

    @Override
    public MapSession createSession() {
        MapSession session = new MapSession();
        session.setMaxInactiveInterval(this.defaultMaxInactiveInterval);
        return session;
    }

    @Override
    public void save(MapSession session) {
        if (!session.getId().equals(session.getOriginalId())) {
            this.sessions.remove(session.getOriginalId());
        }
        this.sessions.put(session.getId(), new MapSession(session));
    }

    @Override
    public MapSession findById(String id) {
        Session saved = this.sessions.get(id);
        if (saved == null) {
            return null;
        }
        if (saved.isExpired()) {
            deleteById(saved.getId());
            return null;
        }
        return new MapSession(saved);
    }

    @Override
    public void deleteById(String id) {
        this.sessions.remove(id);
    }

    @Override
    public Map<String, MapSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
        if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
            return Collections.emptyMap();
        }
        Map<String, MapSession> results = new LinkedHashMap<>();
        for (Session session : this.sessions.values()) {
            MapSession copy = new MapSession(session);
            String principal = this.indexResolver.resolveIndexValueFor(copy);
            if (indexValue.equals(principal)) {
                results.put(copy.getId(), copy);
            }
        }
        return results;
    }
}
