package com.sgtechstack.helloworldauthapp.config;

import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the attributes on the {@code SESSION} cookie as it actually goes out
 * over the wire.
 *
 * <h2>Why a real server and not MockMvc</h2>
 *
 * Every other integration test here uses MockMvc, and MockMvc cannot answer this
 * question. Session cookie attributes are applied by the servlet container from
 * {@code server.servlet.session.cookie.*}, and MockMvc has no container: it uses
 * a {@code MockHttpSession} and emits no {@code Set-Cookie} for it at all. A test
 * written against MockMvc would have had to assert the configuration properties
 * instead — which is a test that the YAML says what the YAML says, and would pass
 * just as happily if the container ignored it.
 *
 * <p>So this class pays for a second application context on a real port. The
 * cookie is the entire credential in this design; being able to read what the
 * browser is actually told about it is worth the cost.
 *
 * <h2>What each attribute is doing</h2>
 *
 * <ul>
 *   <li>{@code HttpOnly} — takes the cookie out of reach of {@code
 *       document.cookie}, so an XSS foothold cannot exfiltrate the session. This
 *       is the reason the SPA holds no token of its own.</li>
 *   <li>{@code SameSite=Lax} — stops the browser attaching the cookie to
 *       cross-site POSTs, which is the first line of CSRF defence and
 *       independent of the token check.</li>
 *   <li>{@code Secure} — withheld in dev, which runs over plaintext by
 *       documented agreement. Asserted absent here rather than left unstated,
 *       and asserted present outside dev in {@link ProductionProfileTest}, so
 *       the dev concession cannot quietly become the default.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class SessionCookieAttributesTest {

    private static final String USERNAME = "cookie-attr-user";
    private static final String PASSWORD = "cookie-attr-password-1234";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Creates its own account rather than relying on the seeded admin: the
        // dev datasource is a named in-memory H2 shared across contexts in this
        // JVM, and other classes clear it wholesale.
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User(USERNAME, "cookie-attr@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER, true));
    }

    @Test
    void theSessionCookieIsHttpOnlyAndSameSiteLax() {
        String setCookie = loginAndReturnSessionSetCookie();

        assertThat(setCookie)
                .as("the session cookie must be unreadable from JavaScript, so an XSS foothold "
                        + "cannot exfiltrate it")
                .containsIgnoringCase("HttpOnly");

        assertThat(setCookie)
                .as("SameSite is CSRF defence independent of the token check")
                .containsIgnoringCase("SameSite=Lax");
    }

    @Test
    void theSessionCookieIsNotMarkedSecureInDevBecauseDevIsPlaintext() {
        String setCookie = loginAndReturnSessionSetCookie();

        // Dev is the one profile that opts out of transport security. Marking the
        // cookie Secure here would mean the browser never sends it back over
        // http, so local development would silently fail to stay logged in.
        // Pinned as a deliberate, profile-scoped exception — ProductionProfileTest
        // asserts the other half.
        assertThat(setCookie).doesNotContainIgnoringCase("Secure");
    }

    @Test
    void theCsrfCookieIsDeliberatelyReadableFromJavascript() {
        // The opposite requirement to the session cookie, and not a mistake. The
        // cookie-plus-header pattern works precisely because the SPA can read this
        // value and echo it back: the same-origin policy stops a hostile page doing
        // the same, so possession of the header proves the request came from a page
        // on an allowed origin.
        //
        // Asserted here rather than in CsrfProtectionTest because MockMvc cannot
        // show it — spring-security-test's csrf() post-processor substitutes the
        // filter's token repository, so no cookie is written once any test in the
        // shared context has used it.
        ResponseEntity<String> csrf = restTemplate.getForEntity("/api/csrf", String.class);

        String setCookie = csrf.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no XSRF-TOKEN cookie was issued"));

        assertThat(setCookie)
                .as("making this HttpOnly looks like hardening and would break every mutation")
                .doesNotContainIgnoringCase("HttpOnly");
    }

    @Test
    void theCookieIsNamedSessionAndCarriesNoPathBeyondRoot() {
        String setCookie = loginAndReturnSessionSetCookie();

        assertThat(setCookie).startsWith("SESSION=");
        assertThat(setCookie).containsIgnoringCase("Path=/");
    }

    /**
     * Logs in through the real filter chain over HTTP and returns the raw
     * {@code Set-Cookie} line for the session cookie.
     *
     * <p>Two round trips, because login is CSRF-protected like every other
     * mutation: fetch the token cookie, then submit the form echoing it as a
     * header. That is the same dance the SPA does.
     */
    private String loginAndReturnSessionSetCookie() {
        ResponseEntity<String> csrf = restTemplate.getForEntity("/api/csrf", String.class);
        String csrfCookie = cookieValue(csrf.getHeaders(), "XSRF-TOKEN")
                .orElseThrow(() -> new AssertionError("no XSRF-TOKEN cookie was issued"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfCookie);
        headers.add("X-XSRF-TOKEN", csrfCookie);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", USERNAME);
        form.add("password", PASSWORD);

        ResponseEntity<String> login = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> setCookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).as("login must set a session cookie").isNotNull();

        return setCookies.stream()
                .filter(cookie -> cookie.startsWith("SESSION="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no SESSION cookie in " + setCookies));
    }

    private static Optional<String> cookieValue(HttpHeaders headers, String name) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return Optional.empty();
        }

        return setCookies.stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .map(cookie -> cookie.substring(name.length() + 1).split(";", 2)[0])
                .findFirst();
    }
}
