package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.MutableClock;
import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.StructuredLogTestCapture;
import com.eitri.testsupport.TestAccounts;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// A fresh context per test gives each scenario an empty in-memory throttle.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:login-ip-throttle;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LoginIpThrottleIT {

    private static final String ATTACKER_IP = "203.0.113.10";
    private static final String OTHER_IP = "198.51.100.7";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private TestAccounts accounts;

    @BeforeEach
    void reset() {
        clock.set(Instant.now());
        accounts = new TestAccounts(jdbc);
        accounts.resetJohndoe();
    }

    @Test
    @DisplayName("[assessment/story3-ac4] failures spread across many usernames from one IP trigger IP throttling")
    void failuresAcrossManyUsernamesThrottleTheIp() throws Exception {
        throttle(ATTACKER_IP);
        assertThat(jdbc.queryForObject("SELECT MAX(failed_login_attempts) FROM users", Integer.class))
                .isLessThan(5);

        try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
            MvcTestResult result = login(ATTACKER_IP, "johndoe", "Password123!");

            assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
            assertThat(result.getResponse().getContentAsString()).isEqualTo("{\"message\":\"Too many requests\"}");

            String event = audit.line("Login rate limit exceeded");
            assertThat(JsonPath.<String>read(event, "$.log.level")).isEqualTo("WARN");
            assertThat(JsonPath.<String>read(event, "$.event.action")).isEqualTo("rate-limit");
            assertThat(JsonPath.<String>read(event, "$.event.outcome")).isEqualTo("failure");
            assertThat(JsonPath.<String>read(event, "$.source.ip")).isEqualTo(ATTACKER_IP);
            assertThat(event).doesNotContain("johndoe", "Password123!", "\"user\":");
        }
    }

    @Test
    @DisplayName("[assessment/story3-ac5] throttled attempts do not count towards any account's lockout")
    void throttledAttemptsNeverReachTheAccountCounter() throws Exception {
        throttle(ATTACKER_IP);
        accounts.resetJohndoe();

        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(login(ATTACKER_IP, "johndoe", "WrongPassword!")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        }

        assertThat(accounts.failedLoginAttempts("johndoe")).isZero();
        assertThat(accounts.lockedUntil("johndoe")).isNull();
    }

    @Test
    @DisplayName("[assessment/story3-ac6] a throttled IP does not block the same account from a different IP")
    void anotherIpCanStillLogIn() throws Exception {
        throttle(ATTACKER_IP);

        assertThat(login(OTHER_IP, "johndoe", "Password123!")).hasStatusOk();
    }

    @Test
    @DisplayName("[assessment/story3-ac7] IP throttling lifts once the window has passed since the last counted failure")
    void throttleLiftsAfterTheWindow() throws Exception {
        throttle(ATTACKER_IP);
        assertThat(login(ATTACKER_IP, "johndoe", "Password123!")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);

        clock.advance(Duration.ofMinutes(15));

        assertThat(login(ATTACKER_IP, "johndoe", "Password123!")).hasStatusOk();
    }

    @Test
    void invalidRequestsAndSuccessesAreNotCounted() throws Exception {
        for (int attempt = 0; attempt < 25; attempt++) {
            assertThat(login(ATTACKER_IP, "", "x")).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(login(ATTACKER_IP, "johndoe", "Password123!")).hasStatusOk();
        }

        assertThat(login(ATTACKER_IP, "nosuchuser", "Password123!")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    /** 20 failed logins from {@code ip}, two against each of 10 unknown usernames: no account locks. */
    private void throttle(String ip) throws Exception {
        for (int user = 0; user < 10; user++) {
            for (int attempt = 0; attempt < 2; attempt++) {
                assertThat(login(ip, "sprayed-user-" + user, "WrongPassword!")).hasStatus(HttpStatus.UNAUTHORIZED);
            }
        }
    }

    private MvcTestResult login(String ip, String username, String password) throws Exception {
        return SessionClient.fetchCsrf(mvc)
                .from(ip)
                .login(mvc, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }
}
