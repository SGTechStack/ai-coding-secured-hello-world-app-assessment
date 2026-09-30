package com.assessment.securedhelloworld.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityBaselineTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorIsNotOpenToAnonymousRequests() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    void corsAllowsConfiguredFrontendOriginWithCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void responsesCarryCspAndNeverALeakingWhitelabelErrorPage() throws Exception {
        mockMvc.perform(get("/api/nonexistent"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
