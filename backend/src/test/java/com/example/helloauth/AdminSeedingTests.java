package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.helloauth.admin.AdminSeeder;
import com.example.helloauth.config.AppProperties;
import com.example.helloauth.user.Role;
import com.example.helloauth.user.User;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * Ticket-13 coverage at the config-driven seeder seam (ticket 05: "admin
 * seed tested via the config-driven seeder"). The context boots with
 * {@code app.admin.*} properties set, so the first (ordered) test observes
 * exactly what the {@code ApplicationRunner} seeded at startup; later tests
 * drive the seeder directly for the restart/idempotence cases. The user
 * fixture rides the shared {@link ApiTestSupport} base.
 */
@SpringBootTest(properties = {
    "app.admin.username=seed-root",
    "app.admin.password=seed-root-password-1",
    "app.admin.email=seed-root@example.com"})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminSeedingTests extends ApiTestSupport {

    @Autowired
    Clock clock;

    @Autowired
    AdminSeeder adminSeeder;

    @Autowired
    AppProperties properties;

    @AfterEach
    void clean() {
        userRepository.deleteAll();
    }

    @Test
    @Order(1)
    void startupSeedsConfiguredAdmin() {
        // This must run before anything deletes users: it observes the row
        // the ApplicationRunner created at context startup — the config →
        // seed wiring end to end.
        User admin = userRepository.findByUsername("seed-root").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getEmail()).isEqualTo("seed-root@example.com");
        assertThat(admin.getCreatedAt()).isNotNull();
        // BCrypt-hashed like any account — never the plaintext.
        assertThat(admin.getPasswordHash())
            .startsWith("$2")
            .isNotEqualTo("seed-root-password-1");
        assertThat(passwordEncoder
            .matches("seed-root-password-1", admin.getPasswordHash())).isTrue();
    }

    @Test
    @Order(2)
    void restartWithExistingAdminSeedsNothing() throws Exception {
        // First run seeds (the runner entry point — what startup invokes);
        // the second is the restart: an ADMIN exists, so nothing is added.
        adminSeeder.run(null);
        adminSeeder.run(null);

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByUsername("seed-root")).isPresent();
    }

    @Test
    @Order(3)
    void existingAdminSuppressesSeedingOfConfiguredAccount() throws Exception {
        // A *different* ADMIN already exists — the idempotence check keys on
        // the role, not the configured username.
        seedUser("existing-root", "existing@example.com", Role.ADMIN);

        adminSeeder.run(null);

        assertThat(userRepository.findByUsername("seed-root")).isEmpty();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @Order(4)
    void blankCredentialsSeedNothing() {
        // Not-configured means stand down quietly (prod's fail-fast lives in
        // profile-required env vars, ticket 14 — not in the seeder).
        AppProperties blank = new AppProperties();
        new AdminSeeder(userRepository, passwordEncoder, clock, blank)
            .seedAdminIfAbsent();

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @Order(5)
    void configuredUsernameTakenByPlainUserSkipsSeed() {
        // The unique constraint would turn an INSERT into a startup crash —
        // the seeder must log and stand down instead.
        seedUser("seed-root", "taken@example.com", Role.USER);

        adminSeeder.seedAdminIfAbsent();

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByUsername("seed-root").orElseThrow()
            .getRole()).isEqualTo(Role.USER);
    }

    @Test
    @Order(6)
    void configuredEmailTakenByPlainUserSkipsSeed() {
        // users.email is unique + non-null — without the guard this INSERT
        // dies on DataIntegrityViolationException and takes startup with
        // it. Same stand-down contract as the username collision.
        seedUser("plain-user", "seed-root@example.com", Role.USER);

        assertThatCode(() -> adminSeeder.seedAdminIfAbsent())
            .doesNotThrowAnyException();

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.existsByRole(Role.ADMIN)).isFalse();
        assertThat(userRepository.findByUsername("seed-root")).isEmpty();
    }
}
