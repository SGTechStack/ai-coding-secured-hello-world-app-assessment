package com.example.hello.auth.session;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.IndexResolver;
import org.springframework.session.MapSession;
import org.springframework.session.PrincipalNameIndexResolver;
import org.springframework.session.Session;

/**
 * In-memory Spring Session store that can also answer "which sessions belong to user X",
 * which logout-everywhere (password reset, disable, delete) needs. Swap for Spring Session
 * JDBC/Redis in a multi-instance deployment; the callers only depend on
 * {@link FindByIndexNameSessionRepository}.
 *
 * <p>The principal lookup is a linear scan. That is fine for a reference app; a real store
 * maintains an index.
 */
public class InMemoryIndexedSessionRepository implements FindByIndexNameSessionRepository<MapSession> {

  private final Map<String, MapSession> sessions = new ConcurrentHashMap<>();
  private final IndexResolver<Session> indexResolver = new PrincipalNameIndexResolver<>();
  private final Duration maxInactiveInterval;

  public InMemoryIndexedSessionRepository(Duration maxInactiveInterval) {
    this.maxInactiveInterval = maxInactiveInterval;
  }

  @Override
  public MapSession createSession() {
    MapSession session = new MapSession();
    session.setMaxInactiveInterval(maxInactiveInterval);
    return session;
  }

  @Override
  public void save(MapSession session) {
    if (!session.getId().equals(session.getOriginalId())) {
      sessions.remove(session.getOriginalId());
    }
    sessions.put(session.getId(), new MapSession(session));
  }

  @Override
  public MapSession findById(String id) {
    MapSession saved = sessions.get(id);
    if (saved == null) {
      return null;
    }
    if (saved.isExpired()) {
      deleteById(id);
      return null;
    }
    return new MapSession(saved);
  }

  @Override
  public void deleteById(String id) {
    sessions.remove(id);
  }

  @Override
  public Map<String, MapSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
    if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName) || indexValue == null) {
      return Map.of();
    }
    Map<String, MapSession> matches = new HashMap<>();
    sessions.forEach(
        (id, session) -> {
          String principal = indexResolver.resolveIndexesFor(session).get(PRINCIPAL_NAME_INDEX_NAME);
          if (!session.isExpired() && indexValue.equals(principal)) {
            matches.put(id, new MapSession(session));
          }
        });
    return matches;
  }

  public int size() {
    return sessions.size();
  }
}
