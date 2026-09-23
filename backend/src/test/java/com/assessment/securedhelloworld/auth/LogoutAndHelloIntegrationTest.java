package com.assessment.securedhelloworld.auth;

import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LogoutAndHelloIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void registerUser(String username, String rawPassword) {
        User user = new User(username, username + "@example.com", passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    private MockHttpSession loginAndCaptureSession(String username, String rawPassword) throws Exception {
        registerUser(username, rawPassword);

        String payload = objectMapper.writeValueAsString(Map.of("username", username, "password", rawPassword));

        MvcResult result = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void helloReturnsGreetingForAuthenticatedSession() throws Exception {
        MockHttpSession session = loginAndCaptureSession("hello-user", "correct-horse-battery");

        mockMvc.perform(get("/api/hello").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, hello-user"));
    }

    @Test
    void helloReturns401WithoutSession() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesSessionAndRejectsReuse() throws Exception {
        MockHttpSession session = loginAndCaptureSession("logout-user", "correct-horse-battery");

        mockMvc.perform(post("/api/logout")
                        .session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        assertThat(session.isInvalid()).isTrue();

        mockMvc.perform(get("/api/hello").session(session))
                .andExpect(status().isUnauthorized());
    }
}
