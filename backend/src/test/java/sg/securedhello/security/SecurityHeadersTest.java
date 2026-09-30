package sg.securedhello.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/** The API's response headers (Std §5:498; IM8 as-9; IM8 as-10; R-HDR-010). */
class SecurityHeadersTest extends CtxDefaultTest {

    private static final String HSTS = "Strict-Transport-Security";
    private static final String CSP = "Content-Security-Policy";

    /** A spread of API responses: success, the 401 entry point, the 403 handler, a 400 and actuator health. */
    static Stream<Arguments> responses() {
        return Stream.of(
                Arguments.of("200 token bootstrap", get("/api/csrf")),
                Arguments.of("401 anonymous", get("/api/profile")),
                Arguments.of("403 CSRF refusal", post("/api/logout")),
                Arguments.of("403 wrong role", get("/api/admin/users").with(user("hdr-user").roles("USER"))),
                Arguments.of("400 malformed login", post("/api/login").contentType(MediaType.TEXT_PLAIN)
                        .content("x")),
                Arguments.of("200 health", get("/actuator/health")));
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    @Test
    @Proves("T-HDR-001")
    void hstsOfAtLeastAYearIsSentOnSecureRequestsOnly() throws Exception {
        String secure = perform(get("/api/csrf").secure(true)).getHeader(HSTS);
        assertThat(secure).isNotNull();
        long maxAge = Arrays.stream(secure.split(";")).map(String::strip)
                .filter(directive -> directive.startsWith("max-age="))
                .mapToLong(directive -> Long.parseLong(directive.substring("max-age=".length())))
                .findFirst().orElseThrow();
        assertThat(maxAge).isGreaterThanOrEqualTo(31_536_000L);

        assertThat(perform(get("/api/csrf").secure(false)).getHeader(HSTS)).as("plain HTTP").isNull();
        assertThat(perform(get("/api/profile").secure(false)).getHeader(HSTS)).as("plain HTTP refusal").isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("responses")
    @Proves("T-HDR-002")
    void everyApiResponseCarriesNosniffFrameOptionsAndTheApiPolicy(String label, MockHttpServletRequestBuilder request)
            throws Exception {
        MockHttpServletResponse response = perform(request);

        assertThat(response.getHeader("X-Content-Type-Options")).as(label).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).as(label).isEqualTo("DENY");
        assertThat(response.getHeader(CSP)).as(label).isEqualTo(SecurityConfig.API_CONTENT_SECURITY_POLICY);
    }

    @Test
    @Proves("T-HDR-002")
    void theApiPolicyFramesNothingAllowsNoScriptAndDiffersFromTheDocumentPolicy() throws IOException {
        List<String> directives = directives(SecurityConfig.API_CONTENT_SECURITY_POLICY);
        assertThat(directives).contains("frame-ancestors 'none'");
        String scriptSources = directives.stream().filter(d -> d.startsWith("script-src ")).findFirst()
                .orElseGet(() -> directives.stream().filter(d -> d.startsWith("default-src ")).findFirst()
                        .orElseThrow());
        assertThat(scriptSources).doesNotContain("'unsafe-inline'", "'unsafe-eval'", "*");

        assertThat(SecurityConfig.API_CONTENT_SECURITY_POLICY).isNotEqualTo(documentPolicy());
    }

    private static List<String> directives(String policy) {
        return Arrays.stream(policy.split(";")).map(String::strip).filter(d -> !d.isEmpty()).toList();
    }

    /** The production document policy, as the SPA's build reads it (ADR-060). */
    private static String documentPolicy() throws IOException {
        String line = Files.readAllLines(Path.of("..", "frontend", ".env.production")).stream()
                .filter(l -> l.startsWith("VITE_CSP=")).findFirst().orElseThrow();
        return line.substring("VITE_CSP=".length()).replace("\"", "");
    }
}
