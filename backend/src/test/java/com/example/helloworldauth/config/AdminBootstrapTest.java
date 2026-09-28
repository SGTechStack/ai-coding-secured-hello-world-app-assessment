package com.example.helloworldauth.config;

import com.example.helloworldauth.user.Role;
import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "app.admin.username=bootstrapadmin",
    "app.admin.email=bootstrapadmin@example.com",
    "app.admin.password=change-me-please-12+"
})
class AdminBootstrapTest {

    private static final String PLAINTEXT = "change-me-please-12+";

    @Autowired
    private AdminBootstrap adminBootstrap;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    @Test
    void seedsSingleAdminWithBcryptHashWhenAbsent() {
        assertThat(users.existsByRole(Role.ADMIN)).isFalse();

        boolean seeded = adminBootstrap.seedIfAbsent();

        assertThat(seeded).isTrue();
        assertThat(users.count()).isEqualTo(1);

        User admin = users.findByUsername("bootstrapadmin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        // BCrypt hash, never plaintext.
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(admin.getPasswordHash()).isNotEqualTo(PLAINTEXT);
        assertThat(passwordEncoder.matches(PLAINTEXT, admin.getPasswordHash())).isTrue();
    }

    @Test
    void isIdempotentWhenAdminAlreadyExists() {
        users.save(new User("existingadmin", "existing@example.com",
            passwordEncoder.encode("some-other-password-12+"), Role.ADMIN));
        assertThat(users.existsByRole(Role.ADMIN)).isTrue();
        long before = users.count();

        boolean seeded = adminBootstrap.seedIfAbsent();

        assertThat(seeded).isFalse();
        assertThat(users.count()).isEqualTo(before);
        // No bootstrap account was created alongside the existing admin.
        assertThat(users.findByUsername("bootstrapadmin")).isEmpty();
    }
}
