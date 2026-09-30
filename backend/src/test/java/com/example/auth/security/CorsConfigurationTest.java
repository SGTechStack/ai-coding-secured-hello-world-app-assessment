package com.example.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
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

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    private static List<String> csv(String value) {
        return value == null ? List.of() : Arrays.stream(value.split(",")).map(String::trim).toList();
    }

    @Test
    void preflightAllowsTheHeadersTheSpaActuallySends() throws Exception {
        String allowHeaders = mockMvc.perform(options("/api/admin/users/1/status")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, X-CSRF-TOKEN, X-Correlation-ID"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andReturn().getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);

        assertThat(csv(allowHeaders))
                .map(String::toLowerCase)
                .contains("content-type", "x-csrf-token", "x-correlation-id");
    }

    @Test
    void preflightAdvertisesExactlyTheApiMethods() throws Exception {
        String allowMethods = mockMvc.perform(options(LOGIN_URL)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);

        assertThat(csv(allowMethods)).containsExactlyInAnyOrder("GET", "POST", "PATCH", "DELETE", "OPTIONS");
    }

    @Test
    void preflightWithAnUnlistedHeaderIsRejected() throws Exception {
        mockMvc.perform(options(LOGIN_URL)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Evil-Header"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflightWithAnUnlistedMethodIsRejected() throws Exception {
        mockMvc.perform(options(LOGIN_URL)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void actualCrossOriginResponseExposesRetryAfterAndCorrelationId() throws Exception {
        String exposed = mockMvc.perform(get("/api/auth/csrf").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().exists("X-Correlation-ID"))
                .andReturn().getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);

        assertThat(csv(exposed)).map(String::toLowerCase).contains("retry-after", "x-correlation-id");
    }
}
