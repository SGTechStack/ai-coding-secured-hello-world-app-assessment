package com.example.helloauth.support;

import com.example.helloauth.domain.Account;
import com.example.helloauth.domain.Role;
import com.example.helloauth.repository.AccountRepository;
import com.example.helloauth.repository.PasswordResetTokenRepository;
import com.example.helloauth.service.IpThrottleService;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for the integration tests: a real server on a real port, a real database, and a client that
 * speaks HTTP.
 *
 * <p>The seam is the API's HTTP boundary rather than any individual class, because that is where the
 * PRD's requirements actually live. Lockout counters, a replayed session cookie, single-use tokens
 * and the admin self-action guards are all server-side state transitions; a unit test of one service
 * can assert that the service did its part and still tell you nothing about whether the endpoint
 * behaves as specified.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
public abstract class ApiIntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";
    protected static final String NEW_PASSWORD = "a-brand-new-password";

    @LocalServerPort protected int port;

    @Autowired protected AccountRepository accounts;
    @Autowired protected PasswordResetTokenRepository resetTokens;
    @Autowired protected IpThrottleService throttle;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected RecordingEmailService emails;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected Clock clock;

    protected ApiClient client;

    /**
     * Returns the application to a known-empty state.
     *
     * <p>Sessions are cleared alongside the accounts. They live in their own tables that nothing in
     * the domain model references, so they survive {@code deleteAll()} — and a session left over from
     * a previous test would make the next one's "not logged in" assumption quietly false.
     *
     * <p>The throttle counter is in memory and keyed by address, and every test in the suite calls
     * from 127.0.0.1, so it is shared state between tests whether or not that was intended.
     */
    @BeforeEach
    void resetApplicationState() {
        resetTokens.deleteAll();
        accounts.deleteAll();
        jdbc.execute("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbc.execute("DELETE FROM SPRING_SESSION");
        throttle.clear();
        emails.clear();
        client = newClient();
    }

    /** A client with an empty cookie jar and a freshly fetched CSRF token. */
    protected ApiClient newClient() {
        return new ApiClient(port).primeCsrf();
    }

    protected Account givenAccount(String username, String email, Role role) {
        return accounts.save(
                new Account(
                        username, email, passwordEncoder.encode(PASSWORD), role, clock.instant()));
    }

    protected Account givenUser(String username) {
        return givenAccount(username, username + "@example.com", Role.USER);
    }

    protected Account givenAdmin(String username) {
        return givenAccount(username, username + "@example.com", Role.ADMIN);
    }

    protected ApiClient.Response login(ApiClient using, String username, String password) {
        return using.post("/api/auth/login", Map.of("username", username, "password", password));
    }

    /** Logs in and asserts nothing — callers that care about the outcome check it themselves. */
    protected ApiClient loggedInAs(String username) {
        ApiClient session = newClient();
        ApiClient.Response response = login(session, username, PASSWORD);
        if (response.status() != 200) {
            throw new AssertionError(
                    "Fixture login for '" + username + "' failed: " + response.status() + " "
                            + response.body());
        }
        return session;
    }
}
