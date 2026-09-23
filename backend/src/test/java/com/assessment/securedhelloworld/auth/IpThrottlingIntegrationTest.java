package com.assessment.securedhelloworld.auth;

import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 3's IP-throttling requirement: repeated failed logins from one IP
 * across DIFFERENT usernames are throttled independently of any single
 * account's own lockout state (app.security.ip-throttle.max-attempts=20,
 * window-minutes=15 per application.yml). All MockMvc requests in this
 * test share the same synthetic remote address, so failures against many
 * distinct (never-registered) usernames simulate one IP hammering the
 * login endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class IpThrottlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String loginPayload(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }

    @Test
    void ipIsThrottledAfterThresholdAcrossDistinctUsernamesIndependentOfAccountLockout() throws Exception {
        // A real account that has never failed a login of its own: proves
        // the throttle triggers on IP activity, not this account's state.
        User untouchedAccount = new User(
                "ip-throttle-bystander", "ip-throttle-bystander@example.com",
                passwordEncoder.encode("correct-horse-battery"));
        userRepository.save(untouchedAccount);

        // Exceed app.security.ip-throttle.max-attempts (20) using distinct,
        // never-registered usernames so no single account's lockout could
        // explain the eventual rejection.
        for (int i = 0; i < 21; i++) {
            mockMvc.perform(post("/api/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginPayload("ip-throttle-nonexistent-" + i, "whatever-password"))
                    .with(SecurityMockMvcRequestPostProcessors.csrf()));
        }

        // The IP is now throttled: even a request bearing the correct
        // credentials for a perfectly good, never-failed account from
        // that same IP is rejected before credentials are even checked.
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("ip-throttle-bystander", "correct-horse-battery"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());

        User reloaded = userRepository.findByUsername("ip-throttle-bystander").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getFailedLoginAttempts())
                .as("the bystander account's own counter must stay untouched: rejection came from IP throttling, not account lockout")
                .isZero();
    }
}
