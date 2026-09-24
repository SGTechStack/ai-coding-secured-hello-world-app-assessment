package com.sgtechstack.helloworldauthapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts the response headers, because a header that is merely configured and
 * never checked is a header that silently disappears in a refactor.
 *
 * <p>Runs under {@code dev} (so no channel enforcement interferes) against a
 * whitelisted route, since these headers should be present on every response
 * regardless of authentication.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityHeadersTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sendsAStrictContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"));
    }

    @Test
    void suppressesTheRefererHeaderEntirely() throws Exception {
        // Directly relevant to the reset flow: a URL that ever carries a token
        // must not leak through Referer to a third party.
        mockMvc.perform(get("/api/health"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void deniesFramingAndContentTypeSniffing() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void restrictsPowerfulBrowserFeatures() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(header().string("Permissions-Policy",
                        "geolocation=(), camera=(), microphone=(), payment=(), usb=()"));
    }

    @Test
    void doesNotAdvertiseHstsOverPlaintext() throws Exception {
        // Sending HSTS over http would be meaningless, and Spring Security
        // correctly withholds it. Asserted so that a future attempt to force
        // the header on unconditionally is caught.
        mockMvc.perform(get("/api/health"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void advertisesHstsOverHttps() throws Exception {
        mockMvc.perform(get("/api/health").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security",
                        "max-age=31536000 ; includeSubDomains"));
    }
}
