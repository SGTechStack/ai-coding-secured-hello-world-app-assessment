package com.eitri.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(properties = "app.security.cors.allowed-origins=http://localhost:5173")
@AutoConfigureMockMvc
class CorsIT {

    private static final String SPA_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("[assessment/story13-ac4] a preflight from the allowed SPA origin may carry credentials and the CSRF header")
    void preflightFromTheAllowedOriginIsGrantedWithCredentials() {
        MvcTestResult result = preflight(SPA_ORIGIN, "X-CSRF-TOKEN, Content-Type");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .headers()
                .hasValue("Access-Control-Allow-Origin", SPA_ORIGIN)
                .hasValue("Access-Control-Allow-Credentials", "true");
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Headers"))
                .containsIgnoringCase("X-CSRF-TOKEN")
                .containsIgnoringCase("Content-Type");
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Methods")).contains("POST");
    }

    @Test
    @DisplayName("[assessment/story13-ac5] a preflight from any other origin is refused")
    void preflightFromAnotherOriginIsRefused() {
        MvcTestResult result = preflight("https://evil.example", "X-CSRF-TOKEN");

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).headers()
                .doesNotContainHeader("Access-Control-Allow-Origin")
                .doesNotContainHeader("Access-Control-Allow-Credentials");
    }

    @Test
    void aCredentialedRequestFromTheAllowedOriginIsGrantedAndOthersAreRefused() {
        assertThat(mvc.get().uri("/api/v1/csrf").header("Origin", SPA_ORIGIN))
                .hasStatusOk()
                .headers()
                .hasValue("Access-Control-Allow-Origin", SPA_ORIGIN)
                .hasValue("Access-Control-Allow-Credentials", "true");

        assertThat(mvc.get().uri("/api/v1/csrf").header("Origin", "https://evil.example"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .headers()
                .doesNotContainHeader("Access-Control-Allow-Origin");
    }

    @Test
    void aPreflightRequestingAnUnlistedHeaderOrMethodIsRefused() {
        assertThat(preflight(SPA_ORIGIN, "X-User-Role")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.options()
                        .uri("/api/v1/auth/login")
                        .header("Origin", SPA_ORIGIN)
                        .header("Access-Control-Request-Method", "PUT"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult preflight(String origin, String requestHeaders) {
        return mvc.options()
                .uri("/api/v1/auth/login")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", requestHeaders)
                .exchange();
    }
}
