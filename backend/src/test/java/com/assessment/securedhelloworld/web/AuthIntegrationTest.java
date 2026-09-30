package com.assessment.securedhelloworld.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Session cookies are asserted via the raw "SESSION" Set-Cookie value, not
 * {@code MockHttpSession}/{@code request.getSession()} — Spring Session wraps the request with
 * its own session inside the filter chain, so the test's own {@code MockHttpServletRequest}
 * never sees a native session even though one was created (a known Spring Session + MockMvc
 * gotcha).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void registerUser() throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", "loginuser",
                        "email", "loginuser@example.com",
                        "password", "correct-horse-battery"))));
    }

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "loginuser", "password", "correct-horse-battery"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    @Test
    void loginWithCorrectCredentialsEstablishesSessionAndAllowsHello() throws Exception {
        Cookie sessionCookie = login();

        mockMvc.perform(get("/api/hello").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello, loginuser"));
    }

    @Test
    void helloWithoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownUsernameReturnIdenticalGenericError() throws Exception {
        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "loginuser", "password", "totally-wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult unknownUsername = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "no-such-user-at-all", "password", "totally-wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(wrongPassword.getResponse().getContentAsString()).isEqualTo(unknownUsername.getResponse().getContentAsString());
    }

    @Test
    void logoutInvalidatesSessionAndReplayIsRejected() throws Exception {
        Cookie sessionCookie = login();

        mockMvc.perform(get("/api/hello").cookie(sessionCookie)).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").with(csrf()).cookie(sessionCookie))
                .andExpect(status().isNoContent());

        // Replaying the same (now-invalidated) session must be rejected as unauthenticated.
        mockMvc.perform(get("/api/hello").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }
}
