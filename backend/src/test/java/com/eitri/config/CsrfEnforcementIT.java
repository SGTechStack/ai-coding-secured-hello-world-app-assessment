package com.eitri.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.LoggedIn;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.TestAccounts;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Every state-changing endpoint rejects a request without X-CSRF-TOKEN, with no side effects. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:csrf-enforcement;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class CsrfEnforcementIT {

    private static final String RESET_TOKEN = "csrf-test-reset-token";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        new TestAccounts(jdbc).restoreSeed();
        jdbc.update(
                "INSERT INTO password_reset_tokens (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                TestAccounts.JOHNDOE_ID,
                com.eitri.passwordreset.PasswordResetTestHashes.hash(RESET_TOKEN),
                Timestamp.from(Instant.now().plus(Duration.ofMinutes(30))));
    }

    @ParameterizedTest(name = "{0}: {1} {2}")
    @CsvSource(delimiter = '|', value = {
        "anonymous | POST   | /api/v1/auth/register                | {\"username\":\"janedoe\",\"email\":\"jane@example.com\",\"password\":\"correct-horse-battery\"}",
        "anonymous | POST   | /api/v1/auth/login                   | {\"username\":\"johndoe\",\"password\":\"Password123!\"}",
        "user      | POST   | /api/v1/auth/logout                  | ",
        "anonymous | POST   | /api/v1/auth/password-reset/request  | {\"email\":\"john@example.com\"}",
        "anonymous | POST   | /api/v1/auth/password-reset/confirm  | {\"token\":\"csrf-test-reset-token\",\"newPassword\":\"a-brand-new-passphrase\"}",
        "admin     | PATCH  | /api/v1/admin/users/{johndoe}/status | {\"enabled\":false}",
        "admin     | PATCH  | /api/v1/admin/users/{johndoe}/role   | {\"role\":\"ADMIN\"}",
        "admin     | DELETE | /api/v1/admin/users/{johndoe}        | "
    })
    @DisplayName("[assessment/story13-ac3] state-changing requests without a valid CSRF token are rejected")
    void stateChangingRequestsWithoutCsrfAreRejected(String client, String method, String path, String body)
            throws Exception {
        Cookie cookie = cookieFor(client);
        Map<String, List<Map<String, Object>>> before = serverState();

        var request = MockMvcRequestBuilders.request(
                        HttpMethod.valueOf(method), path.replace("{johndoe}", TestAccounts.JOHNDOE_ID.toString()))
                .cookie(cookie);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        assertThat(mvc.perform(request)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(serverState()).isEqualTo(before);
        if (!client.equals("anonymous")) {
            assertThat(mvc.get().uri("/api/v1/auth/me").cookie(cookie)).hasStatusOk();
        }
    }

    /** An anonymous client still holds a session (and could fetch a token); it just doesn't send one. */
    private Cookie cookieFor(String client) throws Exception {
        return switch (client) {
            case "anonymous" -> {
                Session session = SessionClient.fetchCsrf(mvc);
                yield session.cookie();
            }
            case "user" -> SessionClient.loggedIn(mvc, "johndoe", TestAccounts.JOHNDOE_PASSWORD).cookie();
            case "admin" -> {
                LoggedIn admin = SessionClient.loggedIn(mvc, "admin", TestAccounts.ADMIN_PASSWORD);
                yield admin.cookie();
            }
            default -> throw new IllegalArgumentException(client);
        };
    }

    /** Accounts, reset tokens and which accounts hold authenticated sessions. */
    private Map<String, List<Map<String, Object>>> serverState() {
        return Map.of(
                "users", jdbc.queryForList("SELECT * FROM users ORDER BY id"),
                "tokens", jdbc.queryForList("SELECT * FROM password_reset_tokens ORDER BY id"),
                "sessions", jdbc.queryForList(
                        "SELECT SESSION_ID, PRINCIPAL_NAME FROM SPRING_SESSION WHERE PRINCIPAL_NAME IS NOT NULL "
                                + "ORDER BY SESSION_ID"));
    }
}
