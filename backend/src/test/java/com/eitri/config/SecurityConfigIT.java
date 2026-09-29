package com.eitri.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void onlyGetHealthIsPublic() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
        assertThat(mvc.head().uri("/actuator/health")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unauthenticatedApiRequestIsRejectedWith401() {
        assertThat(mvc.get().uri("/api/v1/anything")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void csrfTokenEndpointIsPublicAndNeverCached() {
        assertThat(mvc.get().uri("/api/v1/csrf"))
                .hasStatusOk()
                .hasHeader("Cache-Control", "no-store")
                .bodyJson()
                .hasPathSatisfying("$.token", token -> token.assertThat().asString().isNotBlank())
                .extractingPath("$.headerName").isEqualTo("X-CSRF-TOKEN");
    }

    @Test
    void csrfTokenIsHeldInANewServerSideSessionAndNeverInACsrfCookie() throws Exception {
        MvcTestResult result = mvc.get().uri("/api/v1/csrf").exchange();

        assertThat(result).cookies().containsCookie("SESSION").doesNotContainCookie("XSRF-TOKEN");
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("SESSION=", "Path=/", "Secure", "HttpOnly", "SameSite=Lax");
    }

    @Test
    void stateChangingRequestToAPublicEndpointWithoutCsrfTokenIsForbidden() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);

        assertThat(mvc.post().uri("/api/v1/auth/login").cookie(session.cookie()))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void unauthenticatedCsrfFailureOnAProtectedEndpointIsUnauthorized() throws Exception {
        Session anonymous = SessionClient.fetchCsrf(mvc);

        assertThat(mvc.post().uri("/api/v1/anything")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/api/v1/anything").cookie(anonymous.cookie()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authenticatedRequestWithoutCsrfTokenIsForbidden() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        assertThat(session.login(mvc, JOHNDOE_LOGIN)).hasStatusOk();

        assertThat(mvc.post().uri("/api/v1/anything").cookie(session.cookie()))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("[assessment/story13-ac1] the default profile's SESSION cookie is HttpOnly; Secure; SameSite=Lax")
    void loginSetsAHttpOnlySecureSameSiteLaxSessionCookie() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);

        MvcTestResult login = session.login(mvc, JOHNDOE_LOGIN);

        assertThat(login).hasStatusOk();
        assertThat(login.getResponse().getHeader("Set-Cookie"))
                .contains("SESSION=", "HttpOnly", "Secure", "SameSite=Lax");
    }

    @Test
    @DisplayName("[assessment/story13-ac2] logging in rotates the session identifier and leaves the old one unauthenticated")
    void loginRotatesTheSessionIdentifier() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        jakarta.servlet.http.Cookie preLogin = session.cookie();

        assertThat(session.login(mvc, JOHNDOE_LOGIN)).hasStatusOk();

        assertThat(session.sessionId()).isNotEqualTo(SessionClient.sessionId(preLogin));
        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(preLogin)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(session.cookie())).hasStatusOk();
    }

    private static final String JOHNDOE_LOGIN = "{\"username\":\"johndoe\",\"password\":\"Password123!\"}";

    @Test
    void stateChangingRequestWithTheSessionsTokenPassesTheCsrfCheck() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);

        // Past the CSRF filter, the anonymous request then fails authentication.
        assertThat(mvc.post()
                        .uri("/api/v1/anything")
                        .cookie(session.cookie())
                        .header(session.headerName(), session.token()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void stateChangingRequestWithATokenFromAnotherSessionIsForbidden() throws Exception {
        Session stale = SessionClient.fetchCsrf(mvc);
        Session current = SessionClient.fetchCsrf(mvc);

        assertThat(mvc.post()
                        .uri("/api/v1/auth/login")
                        .cookie(current.cookie())
                        .header(stale.headerName(), stale.token()))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/auth/login", "/api/v1/auth/register",
        "/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/confirm"
    })
    void publicStateChangingEndpointsNeedOnlyACsrfToken(String path) throws Exception {
        Session anonymous = SessionClient.fetchCsrf(mvc);

        assertThat(mvc.post().uri(path).cookie(anonymous.cookie())).hasStatus(HttpStatus.FORBIDDEN);
        // With the token an anonymous request reaches the controller (here: a 400 or 202 for an empty body).
        int status = mvc.post()
                .uri(path)
                .cookie(anonymous.cookie())
                .header(anonymous.headerName(), anonymous.token())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange()
                .getResponse()
                .getStatus();
        assertThat(status).isIn(400, 202);
    }

    @Test
    void safeRequestsNeedNoCsrfToken() {
        assertThat(mvc.get().uri("/api/v1/csrf")).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/anything")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void responsesForbidFramingSniffingAndDeviceFeaturesAndCarryAStrictContentSecurityPolicy() {
        assertThat(mvc.get().uri("/actuator/health"))
                .headers()
                .hasValue("Content-Security-Policy", SecurityConfig.CONTENT_SECURITY_POLICY)
                .hasValue("X-Frame-Options", "DENY")
                .hasValue("X-Content-Type-Options", "nosniff")
                .hasValue("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
    }

    @Test
    void secureResponsesCarryHsts() {
        assertThat(mvc.get().uri("/actuator/health").secure(true))
                .headers()
                .hasValue("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains ; preload");
        assertThat(mvc.get().uri("/actuator/health"))
                .headers().doesNotContainHeader("Strict-Transport-Security");
    }

    @Test
    void crossOriginRequestsGetNoCorsGrantWhenNoOriginIsConfigured() {
        assertThat(mvc.get().uri("/api/v1/csrf").header("Origin", "http://localhost:5173"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .headers()
                .doesNotContainHeader("Access-Control-Allow-Origin")
                .doesNotContainHeader("Access-Control-Allow-Credentials");
    }

    @Test
    void h2ConsoleIsClosedOutsideTheDevProfile() {
        assertThat(mvc.get().uri("/h2-console")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordsAreHashedWithBcryptOnlyAndArgon2HashesNoLongerVerify(@Autowired PasswordEncoder encoder) {
        String hash = encoder.encode("Password123!");
        String argon2Hash =
                "{argon2}$argon2id$v=19$m=16384,t=2,p=1$j/IgCqGMPXWxbNSlePIHYw$FCosjaAgqC6GiGKk0NHogagOOz7cJz1xJl8fs/ZdEx8";

        assertThat(hash).startsWith("{bcrypt}$2a$10$");
        assertThat(encoder.matches("Password123!", hash)).isTrue();
        assertThatThrownBy(() -> encoder.matches("Password123!", argon2Hash))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appsDatabaseUserDetailsServiceIsTheOnlyOne(@Autowired ObjectProvider<UserDetailsService> users) {
        assertThat(users.stream().map(service -> service.getClass().getSimpleName()))
                .containsExactly("AccountUserDetailsService");
    }
}
