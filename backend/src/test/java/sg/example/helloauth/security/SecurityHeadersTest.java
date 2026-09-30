package sg.example.helloauth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.TestAccount;

class SecurityHeadersTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    private static final Map<String, String> SECURITY_HEADERS = Map.of(
            "X-Content-Type-Options", "nosniff",
            "X-Frame-Options", "DENY",
            "Content-Security-Policy", "default-src 'self'; object-src 'none'",
            "Permissions-Policy", "geolocation=(), microphone=(), camera=()");

    private static final String HSTS = "max-age=31536000 ; includeSubDomains";

    private static void assertSecurityHeaders(String kind, MvcTestResult result) {
        SECURITY_HEADERS.forEach((name, value) ->
                assertThat(result.getResponse().getHeader(name)).as("%s on a %s response", name, kind).isEqualTo(value));
    }

    private static MockMvcRequestBuilder https(MockMvcRequestBuilder request) {
        return request.secure(true);
    }

    @Test
    void everyResponseOverHttpsCarriesTheSecurityHeadersAndHsts() {
        Browser alice = newBrowser().registerAndLogin(ALICE);
        Browser visitor = newBrowser();
        Map<String, MvcTestResult> responses = Map.of(
                "success", alice.send(https(mvc.get().uri(basePath + "/hello"))),
                "anonymous", visitor.send(https(mvc.get().uri(basePath + "/csrf"))),
                "unauthenticated", visitor.send(https(mvc.get().uri(basePath + "/hello"))),
                "forbidden", visitor.send(https(mvc.post().uri(basePath + "/logout"))),
                "not found", alice.send(https(mvc.get().uri(basePath + "/no-such-thing"))),
                "validation failed", alice.send(https(alice.withCsrf(mvc.post().uri(basePath + "/register")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))));

        responses.forEach((kind, result) -> {
            assertSecurityHeaders(kind, result);
            assertThat(result.getResponse().getHeader("Strict-Transport-Security")).as(kind).isEqualTo(HSTS);
        });
    }

    /** RFC 6797 §7.2: HSTS must not be sent over plain HTTP, where it could be forged anyway. */
    @Test
    void plainHttpResponsesCarryTheSecurityHeadersButNotHsts() {
        MvcTestResult result = newBrowser().get("/csrf");

        assertSecurityHeaders("plain HTTP", result);
        assertThat(result).doesNotContainHeader("Strict-Transport-Security");
    }

    @Test
    void csrfTokenResponseIsNotCacheable() {
        MvcTestResult result = newBrowser().get("/csrf");

        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(result).hasHeader("Pragma", "no-cache").hasHeader("Expires", "0");
    }
}
