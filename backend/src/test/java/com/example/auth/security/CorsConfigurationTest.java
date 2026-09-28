package com.example.auth.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Seam 1 (backend HTTP seam): the frontend now runs on a different origin
 * (see {@code app.cors.allowed-origins}), so a browser sends a CORS preflight
 * before any state-changing cross-origin request. {@code @ActiveProfiles("dev")}
 * pins this to dev's allow-list (http://localhost:3000) regardless of the
 * app's own default active profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CorsConfigurationTest {

    private static final String LOGIN_URL = "/api/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightFromAllowedOriginReceivesCorsHeaders() throws Exception {
        mockMvc.perform(options(LOGIN_URL)
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void preflightFromDisallowedOriginReceivesNoCorsHeaders() throws Exception {
        mockMvc.perform(options(LOGIN_URL)
                        .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
