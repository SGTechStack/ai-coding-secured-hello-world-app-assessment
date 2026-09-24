package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.helloauth.passwordreset.PasswordResetToken;
import com.example.helloauth.passwordreset.PasswordResetTokenRepository;
import com.example.helloauth.user.Role;
import com.example.helloauth.user.User;
import com.example.helloauth.user.UserRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/** Real application restarts: migration ordering, JPA validation and durable sessions. */
class DatabaseMigrationTests {

    @TempDir
    Path directory;

    @Test
    void migrationsPreserveUsersLockoutsTokensAndSessionsAcrossRestart() {
        verifyRestart("jdbc:h2:file:" + directory.resolve("migration").toAbsolutePath(), "sa", "");
    }

    /** Run separately against an EMPTY disposable MySQL or PostgreSQL database. */
    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:(mysql|postgresql):.*")
    void productionVendorMigrationAndRestart() {
        verifyRestart(System.getenv("TEST_DATABASE_URL"),
            System.getenv("TEST_DATABASE_USERNAME"), System.getenv("TEST_DATABASE_PASSWORD"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"${MISSING_DATABASE_URL}", ""})
    void productionWithoutDatabaseUrlDoesNotFallBackToEmbeddedDatabase(String missingUrl) {
        assertThatThrownBy(() -> new SpringApplicationBuilder(HelloAuthApplication.class)
            .profiles("prod").run("--server.port=0", "--DB_URL=" + missingUrl,
                "--DB_USERNAME=test", "--DB_PASSWORD=test",
                "--APP_ADMIN_USERNAME=migration-admin", "--APP_ADMIN_PASSWORD=migration-password"))
            .hasStackTraceContaining("dataSource");
    }

    private void verifyRestart(String url, String username, String password) {
        String name = "migration-" + UUID.randomUUID();
        Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String sessionId;
        Long userId;
        Long tokenId;
        try (var context = start(url, username, password)) {
            User user = new User();
            user.setUsername(name);
            user.setEmail(name + "@example.com");
            user.setPasswordHash("migration-test-hash");
            user.setRole(Role.USER);
            user.setEnabled(true);
            user.setFailedLoginAttempts(3);
            user.setLastFailedAt(timestamp);
            user.setLockedUntil(timestamp.plusSeconds(600));
            user.setCreatedAt(timestamp);
            user = context.getBean(UserRepository.class).saveAndFlush(user);
            userId = user.getId();
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setTokenHash(name);
            token.setExpiresAt(timestamp.plusSeconds(900));
            tokenId = context.getBean(PasswordResetTokenRepository.class).saveAndFlush(token).getId();
            var sessions = sessions(context);
            var session = sessions.createSession();
            session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, name);
            session.setAttribute("binary-roundtrip", "persisted session attribute");
            sessions.save(session);
            sessionId = session.getId();
        }
        try (var context = start(url, username, password)) {
            var users = context.getBean(UserRepository.class);
            var tokens = context.getBean(PasswordResetTokenRepository.class);
            var sessions = sessions(context);
            User restored = users.findById(userId).orElseThrow();
            assertThat(restored.getRole()).isEqualTo(Role.USER);
            assertThat(restored.getFailedLoginAttempts()).isEqualTo(3);
            assertThat(restored.getLastFailedAt()).isEqualTo(timestamp);
            assertThat(restored.getLockedUntil()).isEqualTo(timestamp.plusSeconds(600));
            assertThat(tokens.findById(tokenId).orElseThrow().getExpiresAt())
                .isEqualTo(timestamp.plusSeconds(900));
            assertThat(sessions.findByPrincipalName(name)).containsKey(sessionId);
            assertThat((String) sessions.findById(sessionId).getAttribute("binary-roundtrip"))
                .isEqualTo("persisted session attribute");
            assertThat(context.getBean(JdbcTemplate.class).queryForObject(
                "SELECT COUNT(*) FROM DATABASECHANGELOG", Integer.class)).isEqualTo(2);
            assertThat(users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).count()).isEqualTo(1);
            sessions.deleteById(sessionId);
            tokens.deleteById(tokenId);
            users.deleteById(userId);
        }
    }

    @SuppressWarnings("unchecked")
    private FindByIndexNameSessionRepository<Session> sessions(ConfigurableApplicationContext context) {
        return context.getBean(FindByIndexNameSessionRepository.class);
    }

    private ConfigurableApplicationContext start(String url, String username, String password) {
        return new SpringApplicationBuilder(HelloAuthApplication.class).profiles("prod")
            .run("--server.port=0", "--DB_URL=" + url, "--DB_USERNAME=" + username,
                "--DB_PASSWORD=" + password, "--APP_ADMIN_USERNAME=migration-admin",
                "--APP_ADMIN_PASSWORD=migration-admin-password");
    }
}
