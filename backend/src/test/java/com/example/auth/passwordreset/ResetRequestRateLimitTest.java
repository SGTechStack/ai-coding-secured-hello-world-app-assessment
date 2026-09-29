package com.example.auth.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.example.auth.support.EmailTestConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Reset-request rate limiting (Story 47). Threshold set to 3 to keep the test
 * fast. Verifies the limit engages (429) and, crucially, that within the limit
 * the response is generic 200 regardless of whether the email exists — the
 * limit is IP-keyed and introduces no account-existence leakage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(EmailTestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:resetratetest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "app.reset.request-rate-limit.max-attempts=3",
    "app.reset.request-rate-limit.window-minutes=60",
    "app.admin.bootstrap-enabled=false"   // Clock is mocked; skip startup seeder
})
class ResetRequestRateLimitTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ResetRequestRateLimiter rateLimiter;

    @MockBean
    Clock clock;

    @BeforeEach
    void setup() {
        Mockito.when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        Mockito.when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        rateLimiter.clearForTesting();
    }

    private String freshCsrf() {
        HttpHeaders headers = restTemplate.exchange("/api/auth/csrf", HttpMethod.GET, null, Void.class).getHeaders();
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String c : cookies) {
                if (c.startsWith("XSRF-TOKEN=")) {
                    return c.substring("XSRF-TOKEN=".length()).split(";")[0];
                }
            }
        }
        return null;
    }

    private ResponseEntity<String> requestReset(String email) {
        String csrf = freshCsrf();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-XSRF-TOKEN", csrf);
        h.set(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        return restTemplate.exchange("/api/auth/password-reset/request", HttpMethod.POST,
                new HttpEntity<>(String.format("{\"email\":\"%s\"}", email), h), String.class);
    }

    @Test
    void exceedingRateLimit_returns429() {
        // 3 allowed, 4th throttled
        assertThat(requestReset("a@rate.test").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requestReset("b@rate.test").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requestReset("c@rate.test").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> throttled = requestReset("d@rate.test");
        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(throttled.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .contains("application/problem+json");
    }

    @Test
    void withinLimit_genericResponseRegardlessOfEmailExistence() {
        // Neither email is registered here; both must return identical generic 200
        // within the limit — no account-existence leakage.
        ResponseEntity<String> r1 = requestReset("unknown-1@rate.test");
        ResponseEntity<String> r2 = requestReset("unknown-2@rate.test");

        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r1.getBody()).isEqualTo(r2.getBody());
    }

    @Test
    void throttleResponse_revealsNoAccountInfo() {
        requestReset("x@rate.test");
        requestReset("x@rate.test");
        requestReset("x@rate.test");
        ResponseEntity<String> throttled = requestReset("x@rate.test");

        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(throttled.getBody()).doesNotContain("x@rate.test", "email", "account", "user");
    }
}
