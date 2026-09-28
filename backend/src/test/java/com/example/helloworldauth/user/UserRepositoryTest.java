package com.example.helloworldauth.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordResetTokenRepository tokens;

    private User newUser(String username, String email) {
        return new User(username, email, "$2a$10$hashplaceholder", Role.USER);
    }

    @Test
    void persistsAndFindsByUsernameAndEmail() {
        users.save(newUser("alice", "alice@example.com"));

        assertThat(users.findByUsername("alice")).isPresent();
        assertThat(users.findByEmail("alice@example.com")).isPresent();
        assertThat(users.existsByUsername("alice")).isTrue();
        assertThat(users.existsByEmail("alice@example.com")).isTrue();
        assertThat(users.findByUsername("nobody")).isEmpty();
    }

    @Test
    void setsCreatedAtOnPersist() {
        User saved = users.save(newUser("bob", "bob@example.com"));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getFailedLoginAttempts()).isZero();
    }

    @Test
    void enforcesUniqueUsername() {
        users.saveAndFlush(newUser("carol", "carol@example.com"));
        assertThatThrownBy(() -> users.saveAndFlush(newUser("carol", "carol2@example.com")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesUniqueEmail() {
        users.saveAndFlush(newUser("dave", "dup@example.com"));
        assertThatThrownBy(() -> users.saveAndFlush(newUser("dave2", "dup@example.com")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByRoleReflectsSeededAdmin() {
        assertThat(users.existsByRole(Role.ADMIN)).isFalse();
        users.saveAndFlush(new User("root", "root@example.com", "$2a$10$x", Role.ADMIN));
        assertThat(users.existsByRole(Role.ADMIN)).isTrue();
    }

    @Test
    void storesResetTokenHashWithExpiryAndFindsIt() {
        User u = users.saveAndFlush(newUser("erin", "erin@example.com"));
        tokens.saveAndFlush(new PasswordResetToken(u, "hashed-token", Instant.now().plusSeconds(900)));

        assertThat(tokens.findByTokenHash("hashed-token")).isPresent();
        assertThat(tokens.findByUser(u)).hasSize(1);
        assertThat(tokens.findByTokenHash("hashed-token").get().isUsed()).isFalse();
    }
}
