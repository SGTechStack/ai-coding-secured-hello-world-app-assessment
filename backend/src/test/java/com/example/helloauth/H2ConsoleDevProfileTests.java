package com.example.helloauth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ticket-08 correction coverage: the dev profile enables the H2 console, so
 * its requests must actually pass the security chain. The dev-scoped chain
 * grants the console SAMEORIGIN framing and a CSRF exemption — scoped to
 * /h2-console/** only, leaving the rest of the app on secure defaults.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class H2ConsoleDevProfileTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void h2ConsoleRequestsGetSameOriginFraming() throws Exception {
        // The console is a frameset; DENY would render a blank page. Downstream
        // handling (404 under MockMvc — the console servlet isn't wired into
        // the mock container) is irrelevant; the header proves the dev chain
        // matched the request.
        mockMvc.perform(get("/h2-console/"))
            .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    void h2ConsolePostIsExemptFromCsrf() throws Exception {
        // Without the exemption, CsrfFilter rejects the POST with 403 before
        // any downstream handling.
        mockMvc.perform(post("/h2-console/login.do"))
            .andExpect(status().isNotFound())
            .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    void restOfAppKeepsDenyFraming() throws Exception {
        // 401 since ticket 09 made /api/hello protected; the header assertion
        // is what proves the main chain (not the dev console chain) matched.
        mockMvc.perform(get("/api/hello"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void restOfAppKeepsCsrfOn() throws Exception {
        mockMvc.perform(post("/api/hello"))
            .andExpect(status().isForbidden());
    }
}
