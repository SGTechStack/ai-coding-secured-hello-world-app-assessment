package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.helloauth.domain.Account;
import com.example.helloauth.domain.Role;
import com.example.helloauth.service.AdminBootstrap;
import com.example.helloauth.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Story 12 — the first admin is seeded from configuration, once. */
@TestPropertySource(
        properties = {
            "app.admin.username=seeded-admin",
            "app.admin.email=seeded-admin@example.com",
            "app.admin.password=seeded-admin-password"
        })
class AdminBootstrapTest extends ApiIntegrationTest {

    @Autowired private AdminBootstrap adminBootstrap;

    @Test
    @DisplayName("an empty database gets one ADMIN account with a hashed password")
    void seedsTheFirstAdmin() {
        adminBootstrap.run(null);

        Account seeded = accounts.findByUsername("seeded-admin").orElseThrow();
        assertThat(seeded.getRole()).isEqualTo(Role.ADMIN);
        assertThat(seeded.isEnabled()).isTrue();
        // Hashed identically to any other account — the seed is not a shortcut around BCrypt.
        assertThat(seeded.getPasswordHash()).startsWith("$2");
        assertThat(seeded.getPasswordHash()).doesNotContain("seeded-admin-password");
        assertThat(passwordEncoder.matches("seeded-admin-password", seeded.getPasswordHash()))
                .isTrue();
        assertThat(login(newClient(), "seeded-admin", "seeded-admin-password").status())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("restarting does not accumulate duplicate seed accounts")
    void isIdempotent() {
        adminBootstrap.run(null);
        adminBootstrap.run(null);
        adminBootstrap.run(null);

        assertThat(accounts.count()).isEqualTo(1);
    }

    /**
     * The check is for <em>any</em> admin, not for the configured username. That is deliberate: once a
     * human admin exists the seed has served its purpose, and checking the name instead would mint a
     * second admin the first time someone edited the configured username.
     */
    @Test
    @DisplayName("an existing admin under a different name suppresses the seed")
    void doesNotSeedWhenAnotherAdminExists() {
        givenAdmin("someone-else");

        adminBootstrap.run(null);

        assertThat(accounts.count()).isEqualTo(1);
        assertThat(accounts.findByUsername("seeded-admin")).isEmpty();
    }

    @Test
    @DisplayName("a lone USER account does not suppress the seed")
    void seedsAlongsideNonAdminAccounts() {
        givenUser("alice");

        adminBootstrap.run(null);

        assertThat(accounts.count()).isEqualTo(2);
        assertThat(accounts.findByUsername("seeded-admin")).isPresent();
    }
}
