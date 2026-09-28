package com.assessment.securedhelloworld.auth;

import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies login attempts are counted in the database (not just an
 * in-process Micrometer counter), so the total is correct across every
 * backend instance behind a load balancer, not only the instance that
 * happened to handle each request.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LoginCountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginCountService loginCountService;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private User registerUser(String username, String rawPassword) {
        User user = new User(username, username + "@example.com", passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    private String loginPayload(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }

    @Test
    void successfulLoginIncrementsTheDatabaseBackedSuccessCount() throws Exception {
        registerUser("count-success-user", "correct-horse-battery");
        long before = loginCountService.countFor(LoginOutcome.SUCCESS);

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("count-success-user", "correct-horse-battery"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        assertThat(loginCountService.countFor(LoginOutcome.SUCCESS)).isEqualTo(before + 1);
    }

    @Test
    void failedLoginIncrementsTheDatabaseBackedFailureCount() throws Exception {
        registerUser("count-failure-user", "correct-horse-battery");
        long before = loginCountService.countFor(LoginOutcome.FAILURE);

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("count-failure-user", "totally-wrong-password"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());

        assertThat(loginCountService.countFor(LoginOutcome.FAILURE)).isEqualTo(before + 1);
    }

    @Test
    void incrementingConcurrentlyFromMultipleSimulatedInstancesSumsCorrectly() {
        long before = loginCountService.countFor(LoginOutcome.SUCCESS);

        // Simulates several backend instances (or threads on the same
        // instance) incrementing the same fleet-wide counter concurrently.
        int incrementCount = 20;
        java.util.stream.IntStream.range(0, incrementCount)
                .parallel()
                .forEach(i -> loginCountService.increment(LoginOutcome.SUCCESS));

        assertThat(loginCountService.countFor(LoginOutcome.SUCCESS)).isEqualTo(before + incrementCount);
    }
}
