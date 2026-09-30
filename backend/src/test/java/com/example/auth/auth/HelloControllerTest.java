package com.example.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Seam 1 (backend HTTP seam): the same real-filter-chain style as {@link AuthControllerTest}. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class HelloControllerTest {

    private static final String HELLO_URL = "/api/hello";
    /** Spring Session's cookie: the session travels as a real cookie, exactly like a browser. */
    private static final String SESSION_COOKIE_NAME = "SESSION";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String VALID_USERNAME = "johndoe";
    private static final String VALID_PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Seeds {@code VALID_USERNAME} on first use -- no data.sql exists to do it anymore. */
    @BeforeEach
    void seedUser() {
        if (userRepository.findByUsername(VALID_USERNAME).isEmpty()) {
            userRepository.save(
                    new User(VALID_USERNAME, "johndoe@example.com", passwordEncoder.encode(VALID_PASSWORD), "John"));
        }
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(HELLO_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestReturnsGreetingAsPlainText() throws Exception {
        Cookie session = login(VALID_USERNAME, VALID_PASSWORD);

        mockMvc.perform(get(HELLO_URL).cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Hello, " + VALID_USERNAME));
    }

    private Cookie mintCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")).andReturn();
        Cookie csrfCookie = result.getResponse().getCookie(CSRF_COOKIE_NAME);
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    private Cookie login(String username, String password) throws Exception {
        Cookie csrfCookie = mintCsrfCookie();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password)))
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = result.getResponse().getCookie(SESSION_COOKIE_NAME);
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }
}
