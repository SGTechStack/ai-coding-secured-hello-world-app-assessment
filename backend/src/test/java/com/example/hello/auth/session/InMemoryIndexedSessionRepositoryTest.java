package com.example.hello.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

class InMemoryIndexedSessionRepositoryTest {

  private final InMemoryIndexedSessionRepository repository =
      new InMemoryIndexedSessionRepository(Duration.ofMinutes(30));

  @Test
  void savesAndFindsCopies() {
    MapSession session = repository.createSession();
    session.setAttribute("k", "v");
    repository.save(session);

    MapSession found = repository.findById(session.getId());
    assertThat(found).isNotNull();
    assertThat(found.<String>getAttribute("k")).isEqualTo("v");
    assertThat(found.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
    assertThat(found).isNotSameAs(session);
  }

  @Test
  void changingTheIdDropsTheOldEntry() {
    MapSession session = repository.createSession();
    repository.save(session);
    String oldId = session.getId();

    String newId = session.changeSessionId();
    repository.save(session);

    assertThat(repository.findById(oldId)).isNull();
    assertThat(repository.findById(newId)).isNotNull();
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void expiredSessionsAreEvictedOnLookup() {
    MapSession session = repository.createSession();
    session.setMaxInactiveInterval(Duration.ofMinutes(1));
    session.setLastAccessedTime(Instant.now().minus(Duration.ofMinutes(5)));
    repository.save(session);

    assertThat(repository.findById(session.getId())).isNull();
    assertThat(repository.size()).isZero();
  }

  @Test
  void findsSessionsByPrincipalName() {
    MapSession alice1 = repository.createSession();
    alice1.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "alice");
    MapSession alice2 = repository.createSession();
    alice2.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "alice");
    MapSession bob = repository.createSession();
    bob.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "bob");
    repository.save(alice1);
    repository.save(alice2);
    repository.save(bob);

    assertThat(repository.findByPrincipalName("alice")).containsOnlyKeys(alice1.getId(), alice2.getId());
    assertThat(repository.findByPrincipalName("nobody")).isEmpty();
    assertThat(repository.findByIndexNameAndIndexValue("other-index", "alice")).isEmpty();
  }
}
