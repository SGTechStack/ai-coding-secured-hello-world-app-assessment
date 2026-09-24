package com.example.demo_app.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Security response headers on every API response, and no HTTPS redirect outside prod. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersTest {

  @Autowired private MockMvc mvc;

  @Test
  void responsesCarryCspReferrerAndPermissionsPolicies() throws Exception {
    mvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isNoContent())
        .andExpect(
            header()
                .string("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(
            header().string("Permissions-Policy", "camera=(), geolocation=(), microphone=()"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"));
  }

  @Test
  void errorResponsesCarryTheHeadersToo() throws Exception {
    mvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("Content-Security-Policy"))
        .andExpect(header().exists("Referrer-Policy"))
        .andExpect(header().exists("Permissions-Policy"));
  }

  @Test
  void plainHttpIsServedWithoutRedirectOutsideProd() throws Exception {
    mvc.perform(get("http://localhost/api/v1/auth/csrf"))
        .andExpect(status().isNoContent())
        .andExpect(header().doesNotExist("Location"));
  }
}
