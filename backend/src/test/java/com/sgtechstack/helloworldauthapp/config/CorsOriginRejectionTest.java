package com.sgtechstack.helloworldauthapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CORS allow-list is validated at startup ({@link CorsOriginValidationTest})
 * and was never observed actually refusing anything at request time.
 *
 * <p>Those are different properties. Startup validation proves the configured
 * list is well-formed; it says nothing about whether the filter consults it. With
 * {@code allowCredentials: true}, any origin the browser is told is acceptable
 * can make authenticated requests carrying a visitor's session cookie and read
 * the responses — so this is the control that decides which pages on the internet
 * can act as a signed-in user, and it was resting on configuration nobody had
 * seen take effect.
 *
 * <p>Preflight is the right place to assert it. A browser sends {@code OPTIONS}
 * before any cross-origin request that carries credentials or a custom header,
 * which covers every mutation this API exposes, and a refusal there means the
 * real request is never sent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CorsOriginRejectionTest {

    /** The dev profile's configured origin. */
    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightFromAnUnknownOriginIsRefused() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void preflightFromTheAllowedOriginSucceedsAndPermitsCredentials() throws Exception {
        // The control case. Without it, the refusal above could be explained by
        // CORS being broken outright rather than by the allow-list working.
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                // Without this the SPA could not send the session cookie at all,
                // so the entire cookie-auth design depends on it.
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void aSimpleRequestFromAnUnknownOriginGetsNoAllowOriginHeader() throws Exception {
        // A GET with no custom headers is not preflighted, so the server does
        // answer it — a browser cannot stop the request, only the reading of the
        // response. Withholding Access-Control-Allow-Origin is what enforces
        // that, and is therefore the thing to assert.
        mockMvc.perform(get("/api/health").header("Origin", "http://evil.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void anOriginThatMerelyLooksLikeTheAllowedOneIsRefused() throws Exception {
        // Suffix and prefix confusion is the classic CORS allow-list bug: a
        // check written as "contains" or "endsWith" accepts both of these.
        // Spring's exact matching does not, and this pins that it stays exact.
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000.evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());

        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://evil-localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
