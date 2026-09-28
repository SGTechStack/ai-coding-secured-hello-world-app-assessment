package com.assessment.securedhelloworld.auth;

import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private User registerUser(String username, String rawPassword) {
        User user = new User(username, username + "@example.com", passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    private String loginPayload(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }

    @Test
    void loginSucceedsWithCorrectCredentialsAndResetsFailedAttempts() throws Exception {
        User user = registerUser("login-success-user", "correct-horse-battery");
        user.setFailedLoginAttempts(3);
        userRepository.save(user);

        MvcResult result = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("login-success-user", "correct-horse-battery"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();
        assertThat(result.getRequest().getSession(false).getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();

        User reloaded = userRepository.findByUsername("login-success-user").orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();
    }

    @Test
    void loginFailsWithWrongPasswordUsingGenericError() throws Exception {
        registerUser("login-wrongpass-user", "correct-horse-battery");

        MvcResult wrongPasswordResult = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("login-wrongpass-user", "totally-wrong-password"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult unknownUserResult = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("no-such-user-at-all", "whatever-password"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(wrongPasswordResult.getResponse().getContentAsString())
                .isEqualTo(unknownUserResult.getResponse().getContentAsString());

        User reloaded = userRepository.findByUsername("login-wrongpass-user").orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void loginRejectedWhileAccountLocked() throws Exception {
        User user = registerUser("login-locked-user", "correct-horse-battery");
        user.setLockedUntil(Instant.now().plusSeconds(900));
        userRepository.save(user);

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("login-locked-user", "correct-horse-battery"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());
    }
}
