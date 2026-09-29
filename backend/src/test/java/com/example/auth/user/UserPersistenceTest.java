package com.example.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.example.auth.passwordreset.PasswordResetToken;
import com.example.auth.passwordreset.PasswordResetTokenRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class UserPersistenceTest {

    @Autowired
    UserRepository users;

    @Autowired
    PasswordResetTokenRepository tokens;

    private User newUser(String username, String email) {
        return new User(username, email, "$2a$10$hash", Role.USER, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void persistsUserWithGeneratedUuidAndDefaults() {
        User saved = users.saveAndFlush(newUser("alice", "alice@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getFailedLoginAttempts()).isZero();
        assertThat(saved.getLockedUntil()).isNull();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void enforcesUniqueUsername() {
        users.saveAndFlush(newUser("alice", "alice@example.com"));

        assertThatThrownBy(() -> users.saveAndFlush(newUser("alice", "other@example.com")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesUniqueEmail() {
        users.saveAndFlush(newUser("alice", "alice@example.com"));

        assertThatThrownBy(() -> users.saveAndFlush(newUser("bob", "alice@example.com")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsByUsernameAndEmailAndExistenceChecks() {
        users.saveAndFlush(newUser("alice", "alice@example.com"));

        assertThat(users.findByUsername("alice")).isPresent();
        assertThat(users.findByEmail("alice@example.com")).isPresent();
        assertThat(users.existsByUsername("alice")).isTrue();
        assertThat(users.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void existsByRoleReflectsSeededAdmin() {
        assertThat(users.existsByRole(Role.ADMIN)).isFalse();
        users.saveAndFlush(new User("root", "root@example.com", "$2a$10$hash", Role.ADMIN,
            Instant.parse("2026-01-01T00:00:00Z")));
        assertThat(users.existsByRole(Role.ADMIN)).isTrue();
    }

    @Test
    void persistsResetTokenLinkedToUserAndFindsByHash() {
        User alice = users.saveAndFlush(newUser("alice", "alice@example.com"));
        Instant expiry = Instant.parse("2026-01-01T00:30:00Z");
        tokens.saveAndFlush(new PasswordResetToken(alice, "tokenhash123", expiry));

        PasswordResetToken found = tokens.findByTokenHash("tokenhash123").orElseThrow();
        assertThat(found.getUser().getId()).isEqualTo(alice.getId());
        assertThat(found.isUsed()).isFalse();
        assertThat(found.isExpired(Instant.parse("2026-01-01T00:15:00Z"))).isFalse();
        assertThat(found.isExpired(Instant.parse("2026-01-01T00:45:00Z"))).isTrue();
    }
}
