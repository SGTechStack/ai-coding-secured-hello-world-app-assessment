package com.assessment.securedhelloworld.web;

import com.assessment.securedhelloworld.domain.User;
import com.assessment.securedhelloworld.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers PRD Story 3 / ticket 03: account lockout after repeated failed logins, cooldown-expiry
 * reset, and IP-level throttling that is independent of any single account's lockout state.
 * <p>
 * {@code @SpringBootTest} caches (and reuses) the Spring context — and therefore the singleton
 * {@code IpLoginThrottleService} bean and the H2 database — across every test method in this
 * class, and MockMvc requests share the same simulated remote address unless told otherwise.
 * To keep the three tests independent of execution order, each uses its own registered username
 * AND its own fake source IP (set via a {@link RequestPostProcessor} on {@code request.setRemoteAddr}),
 * rather than relying on {@code @BeforeEach}/{@code MockHttpSession} isolation.
 * <p>
 * Session cookies aren't needed here since lockout/throttling is asserted purely from the login
 * response status and repository state, unlike {@link AuthIntegrationTest} which extracts the
 * SESSION cookie for follow-up requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LockoutIntegrationTest {

    private static final String CORRECT_PASSWORD = "correct-horse-battery";
    private static final String WRONG_PASSWORD = "totally-wrong-password";

    // Matches app.security.lockout.max-attempts in application-test.yml.
    private static final int MAX_ATTEMPTS = 5;

    // Matches app.security.ip-throttle.max-failures in application-test.yml.
    private static final int IP_MAX_FAILURES = 20;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private void register(String username, String ip) throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf()).with(fromIp(ip)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "email", username + "@example.com",
                        "password", CORRECT_PASSWORD))));
    }

    private MockHttpServletRequestBuilder loginRequest(String username, String password, String ip) throws Exception {
        return post("/api/auth/login").with(csrf()).with(fromIp(ip)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password)));
    }

    @Test
    void nConsecutiveFailuresLockAccountEvenWithCorrectPasswordAfterwards() throws Exception {
        String username = "lockout-basic";
        String ip = "10.0.1.1";
        register(username, ip);

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            mockMvc.perform(loginRequest(username, WRONG_PASSWORD, ip))
                    .andExpect(status().isUnauthorized());
        }

        User locked = userRepository.findByUsername(username).orElseThrow();
        assertThat(locked.isLocked()).isTrue();
        assertThat(locked.getFailedLoginAttempts()).isGreaterThanOrEqualTo(MAX_ATTEMPTS);

        // (N+1)th attempt, this time with the CORRECT password, must still be rejected — the
        // account is locked, so Spring Security's pre-auth check rejects it before the password
        // is even compared.
        mockMvc.perform(loginRequest(username, CORRECT_PASSWORD, ip))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void successfulLoginAfterCooldownElapsedResetsFailedAttemptCounter() throws Exception {
        String username = "lockout-cooldown";
        String ip = "10.0.1.2";
        register(username, ip);

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            mockMvc.perform(loginRequest(username, WRONG_PASSWORD, ip));
        }

        User locked = userRepository.findByUsername(username).orElseThrow();
        assertThat(locked.isLocked()).isTrue();

        // Simulate the cooldown having elapsed, without waiting real minutes.
        locked.setLockedUntil(null);
        userRepository.save(locked);

        mockMvc.perform(loginRequest(username, CORRECT_PASSWORD, ip))
                .andExpect(status().isOk());

        User afterSuccess = userRepository.findByUsername(username).orElseThrow();
        assertThat(afterSuccess.getFailedLoginAttempts()).isZero();
        assertThat(afterSuccess.getLockedUntil()).isNull();
    }

    @Test
    void ipThrottlingEngagesIndependentlyOfAnySingleAccountLockout() throws Exception {
        String ip = "10.0.1.3";

        // Fail more than the IP threshold using a DIFFERENT nonexistent username each time, so no
        // single account ever accumulates enough failures to lock — proving the throttle is
        // IP-wide, not a side effect of any one account's lockout.
        for (int i = 0; i < IP_MAX_FAILURES + 1; i++) {
            mockMvc.perform(loginRequest("no-such-user-" + i, WRONG_PASSWORD, ip));
        }

        mockMvc.perform(loginRequest("yet-another-user", WRONG_PASSWORD, ip))
                .andExpect(status().isTooManyRequests());
    }
}
