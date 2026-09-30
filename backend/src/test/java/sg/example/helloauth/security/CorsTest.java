package sg.example.helloauth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sg.example.helloauth.support.IntegrationTest;

class CorsTest extends IntegrationTest {

    private MvcTestResult preflight(String origin, String requestHeaders) {
        return mvc.options().uri(basePath + "/login")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", requestHeaders)
                .exchange();
    }

    @Test
    void allowedFrontendOriginPassesPreflightWithCredentials() {
        MvcTestResult result = preflight(FRONTEND_ORIGIN, "X-CSRF-TOKEN");

        assertThat(result).hasStatusOk()
                .hasHeader("Access-Control-Allow-Origin", FRONTEND_ORIGIN)
                .hasHeader("Access-Control-Allow-Credentials", "true")
                .hasHeader("Access-Control-Max-Age", "3600");
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Methods"))
                .isEqualTo("GET,POST,PUT,PATCH,DELETE,OPTIONS");
    }

    @Test
    void otherOriginIsRefused() {
        MvcTestResult result = preflight("https://evil.example.com", "X-CSRF-TOKEN");

        assertThat(result).hasStatus(403).doesNotContainHeader("Access-Control-Allow-Origin");
    }

    @Test
    void headersOutsideTheAllowListAreRefused() {
        assertThat(preflight(FRONTEND_ORIGIN, "Authorization")).hasStatus(403);
    }

    /** Retry-After isn't a CORS-safelisted response header, so the SPA could not read it otherwise. */
    @Test
    void spaCanReadRetryAfter() {
        MvcTestResult result = mvc.get().uri(basePath + "/csrf").header("Origin", FRONTEND_ORIGIN).exchange();

        assertThat(result).hasStatusOk().hasHeader("Access-Control-Expose-Headers", "Retry-After");
    }
}
