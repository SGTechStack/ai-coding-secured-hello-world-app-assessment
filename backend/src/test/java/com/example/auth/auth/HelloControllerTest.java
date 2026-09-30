package com.example.auth.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.security.ratelimit.RateLimiters;
import com.example.auth.support.ApiSession;
import com.example.auth.support.TestUsers;
import com.example.auth.user.Role;
import com.example.auth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Seam 1 (backend HTTP seam): the same real-session style as {@link AuthControllerTest}. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class HelloControllerTest {

    private static final String HELLO_URL = "/api/hello";
    private static final String USERNAME = "hello-johndoe";
    private static final String PASSWORD = "HelloPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiters rateLimiters;

    @BeforeEach
    void setUp() {
        TestUsers.reset(userRepository, passwordEncoder, USERNAME, PASSWORD, Role.USER);
        rateLimiters.resetAll();
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        new ApiSession(mockMvc, objectMapper).get(HELLO_URL)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void authenticatedRequestReturnsGreetingAsPlainText() throws Exception {
        ApiSession client = new ApiSession(mockMvc, objectMapper);
        client.login(USERNAME, PASSWORD).andExpect(status().isOk());

        client.get(HELLO_URL)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Hello, " + USERNAME));
    }
}
