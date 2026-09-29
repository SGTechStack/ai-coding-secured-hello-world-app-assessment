package com.eitri.greeting;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.MutableClock;
import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:greeting;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class GreetingIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.set(Instant.now());
    }

    @ParameterizedTest(name = "{0} ({1})")
    @CsvSource({"johndoe, Password123!", "admin, test-only-admin-password"})
    @DisplayName("[assessment/story5-ac1] an authenticated USER or ADMIN is greeted by username")
    void authenticatedUserIsGreetedByUsername(String username, String password) throws Exception {
        Session session = login(username, password);

        MvcTestResult result = mvc.get().uri("/api/v1/hello").cookie(session.cookie()).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.TEXT_PLAIN);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("Hello, " + username);
    }

    @Test
    @DisplayName("[assessment/story5-ac2] a request without a session cookie is unauthorized")
    void noSessionCookieIsUnauthorized() throws Exception {
        assertUnauthorizedWithoutGreeting(mvc.get().uri("/api/v1/hello").exchange());
    }

    @Test
    @DisplayName("[assessment/story5-ac2] a tampered session cookie is unauthorized")
    void tamperedSessionCookieIsUnauthorized() throws Exception {
        Session session = login("johndoe", "Password123!");
        String forged = Base64.getEncoder()
                .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));

        assertUnauthorizedWithoutGreeting(
                mvc.get().uri("/api/v1/hello").cookie(new Cookie("SESSION", forged)).exchange());
        assertUnauthorizedWithoutGreeting(mvc.get()
                .uri("/api/v1/hello")
                .cookie(new Cookie("SESSION", session.cookie().getValue() + "x"))
                .exchange());
    }

    @Test
    @DisplayName("[assessment/story5-ac2] a session past its absolute lifetime is unauthorized")
    void sessionPastAbsoluteLifetimeIsUnauthorized() throws Exception {
        Session session = login("johndoe", "Password123!");
        clock.advance(Duration.ofHours(8).plusSeconds(1));

        assertUnauthorizedWithoutGreeting(mvc.get().uri("/api/v1/hello").cookie(session.cookie()).exchange());
    }

    private Session login(String username, String password) throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        assertThat(session.login(
                        mvc, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .hasStatusOk();
        return session;
    }

    private static void assertUnauthorizedWithoutGreeting(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(401);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Hello");
    }
}
