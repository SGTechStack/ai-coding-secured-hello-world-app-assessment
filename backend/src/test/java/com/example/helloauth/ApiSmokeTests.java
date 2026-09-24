package com.example.helloauth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ticket-08 smoke coverage at the HTTP seam: the ping endpoint, actuator
 * health, and the CORS allow-list that the cross-origin SPA depends on.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTests {

    private static final String SPA_ORIGIN = "http://localhost:3000";
    private static final String DISALLOWED_ORIGIN = "https://evil.example.com";

    @Autowired
    MockMvc mockMvc;

    @Test
    void helloRejectsAnonymousCallers() throws Exception {
        // Since ticket 09 /api/hello is a protected endpoint — anonymous gets
        // 401, never a redirect or 403. Personalized greeting coverage lives
        // in AuthFlowTests.
        mockMvc.perform(get("/api/hello"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorHealthReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void corsPreflightAllowsSpaOriginWithCredentials() throws Exception {
        mockMvc.perform(options("/api/hello")
                .header("Origin", SPA_ORIGIN)
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", SPA_ORIGIN))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void corsPreflightRejectsDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/hello")
                .header("Origin", DISALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void corsSimpleRequestFromDisallowedOriginIsRejected() throws Exception {
        mockMvc.perform(get("/api/hello").header("Origin", DISALLOWED_ORIGIN))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void defenseInDepthHeadersAreEmittedOnApiResponses() throws Exception {
        // The main chain's headers block (F-01 remediation): this is a
        // JSON-only API — no HTML, script, or frame rendering — so the CSP
        // denies every source and ancestor outright. Referrer-Policy keeps
        // the ?token= reset-link URL from leaking cross-origin via Referer.
        // The SPA's own headers are its static host's job, out of repo scope.
        mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'"))
            .andExpect(header().string("Referrer-Policy",
                "strict-origin-when-cross-origin"))
            .andExpect(header().string("Permissions-Policy",
                "camera=(), microphone=(), geolocation=()"));
    }
}
