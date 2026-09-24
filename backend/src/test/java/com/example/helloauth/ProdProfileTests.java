package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.helloauth.admin.AdminSeeder;
import com.example.helloauth.user.Role;
import com.example.helloauth.user.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Ticket-14 coverage of the {@code prod} profile: Secure session cookies,
 * env-driven CORS origins, H2 console off, and admin seed creds bound from
 * the environment (here via test properties, which resolve the same
 * {@code APP_ADMIN_*}/{@code APP_CORS_*} placeholders). Missing-credential
 * fail-fast is pinned separately in {@link StartupConfigValidationTests}.
 * HTTP plumbing rides the shared {@link ApiTestSupport} fixture.
 */
@SpringBootTest(properties = {
    "DB_URL=jdbc:h2:mem:prod-profile-tests",
    "DB_USERNAME=sa",
    "DB_PASSWORD=",
    "APP_ADMIN_USERNAME=prod-root",
    "APP_ADMIN_PASSWORD=prod-root-password-1",
    "APP_ADMIN_EMAIL=prod-root@example.com",
    "APP_CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com"})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdProfileTests extends ApiTestSupport {

    @Autowired
    ServerProperties serverProperties;

    @Autowired
    CorsConfigurationSource corsConfigurationSource;

    @Autowired
    Environment environment;

    @Autowired
    AdminSeeder adminSeeder;

    @BeforeEach
    void clean() {
        // Tokens first — password_reset_tokens.user_id FKs into users.
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        // Re-seed the env-configured admin — the startup seed was wiped with
        // the rest of the (shared in-memory) tables.
        adminSeeder.seedAdminIfAbsent();
    }

    @Test
    void sessionCookieIsSecureUnderProd() {
        assertThat(serverProperties.getServlet().getSession().getCookie()
            .getSecure()).isTrue();
    }

    @Test
    void loginSetsSecureSessionCookieEndToEnd() throws Exception {
        // Prove the property actually reaches the wire: a real login's
        // Set-Cookie carries Secure under prod.
        Cookie session = loginSession("prod-root", "prod-root-password-1");
        assertThat(session.getSecure()).isTrue();
    }

    @Test
    void corsAllowListComesFromTheEnvironment() {
        CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(
            new MockHttpServletRequest("GET", "/api/hello"));
        assertThat(config.getAllowedOrigins())
            .containsExactlyInAnyOrder(
                "https://app.example.com", "https://admin.example.com")
            .doesNotContain("http://localhost:3000");
    }

    @Test
    void h2ConsoleIsOffUnderProd() {
        assertThat(environment.getProperty(
            "spring.h2.console.enabled", Boolean.class)).isFalse();
    }

    @Test
    void resetLinkLoggingIsSuppressedUnderProd() {
        // Security-review F-02: prod must never write the plaintext reset
        // token to logs — the tunable binds false here while the default
        // profile keeps the developer-facing link line.
        assertThat(environment.getProperty(
            "app.password-reset.log-reset-link", Boolean.class)).isFalse();
    }

    @Test
    void adminSeedCredsBindFromTheEnvironment() {
        // The seeder ran at startup off the env-bound creds — the account
        // exists and carries ADMIN (the login test above proves it
        // authenticates).
        User admin = userRepository.findByUsername("prod-root").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getEmail()).isEqualTo("prod-root@example.com");
    }
}
