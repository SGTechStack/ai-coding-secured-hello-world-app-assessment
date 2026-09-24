package com.sgtechstack.helloworldauthapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code app.security} block in {@code application.yml} binds
 * onto {@link SecurityProperties} exactly as the URL guard matrix design
 * expects. This is a binding test, not a behavioural one: {@link
 * SecurityConfigTest} covers the filter chain actually enforcing these
 * values.
 */
@SpringBootTest
@ActiveProfiles("dev")
class SecurityPropertiesTest {

    @Autowired
    private SecurityProperties securityProperties;

    @Test
    void roleMappingsAreBoundFromYaml() {
        assertThat(securityProperties.roleMappings())
                .containsEntry("ADMIN", List.of("ADMIN_USER_READ", "ADMIN_USER_EMAIL_READ", "ADMIN_USER_WRITE"))
                .containsEntry("USER", List.of("HELLO_READ", "ACCOUNT_SELF_MANAGE"));
    }

    @Test
    void readingAUsersEmailIsASeparateAuthorityFromListingUsers() {
        // Split on purpose. Listing accounts and reading somebody's actual email
        // address are different acts with different sensitivity, and keeping the
        // authorities distinct is what would let a future support or read-only
        // role hold the listing without the personal data behind it. Both land
        // on ADMIN today; the seam is the point.
        assertThat(securityProperties.roleMappings().get("ADMIN"))
                .contains("ADMIN_USER_READ", "ADMIN_USER_EMAIL_READ");

        assertThat(securityProperties.urlGuards().get("ADMIN_USER_EMAIL_READ"))
                .containsExactly(new SecurityProperties.UrlGuard("GET", "/api/admin/users/*/email"));
    }

    @Test
    void selfServiceRightsAreGuardedAndTakeNoAccountId() {
        // Neither route carries an account id: the subject is always the
        // authenticated principal, so there is no parameter one user could
        // tamper with to reach another's data.
        assertThat(securityProperties.urlGuards().get("ACCOUNT_SELF_MANAGE"))
                .containsExactlyInAnyOrder(
                        new SecurityProperties.UrlGuard("GET", "/api/account/export"),
                        new SecurityProperties.UrlGuard("DELETE", "/api/account"));

        assertThat(securityProperties.urlGuards().get("ACCOUNT_SELF_MANAGE"))
                .allSatisfy(guard -> assertThat(guard.path()).doesNotContain("*"));
    }

    @Test
    void roleHierarchyIsBoundFromYaml() {
        assertThat(securityProperties.roleHierarchy()).isEqualTo("ROLE_ADMIN > ROLE_USER");
    }

    @Test
    void urlGuardsAreBoundFromYamlWithCorrectMethodsAndPaths() {
        assertThat(securityProperties.urlGuards().get("HELLO_READ"))
                .containsExactly(new SecurityProperties.UrlGuard("GET", "/api/hello"));

        assertThat(securityProperties.urlGuards().get("ADMIN_USER_READ"))
                .containsExactly(new SecurityProperties.UrlGuard("GET", "/api/admin/users"));

        assertThat(securityProperties.urlGuards().get("ADMIN_USER_WRITE"))
                .containsExactlyInAnyOrder(
                        new SecurityProperties.UrlGuard("PATCH", "/api/admin/users/*/enabled"),
                        new SecurityProperties.UrlGuard("PATCH", "/api/admin/users/*/role"),
                        new SecurityProperties.UrlGuard("DELETE", "/api/admin/users/*")
                );
    }

    @Test
    void whitelistContainsEveryPubliclyReachableRoute() {
        assertThat(securityProperties.whitelist()).containsExactlyInAnyOrder(
                "/api/auth/register",
                // Readable before an account exists, because the person deciding
                // whether to hand over an email address has not registered yet.
                "/api/privacy-notice",
                "/api/health",
                "/api/csrf",
                "/api/auth/login",
                "/api/auth/logout",
                "/api/auth/password-reset/request",
                "/api/auth/password-reset/confirm"
        );
    }

    @Test
    void rateLimitsCoverEveryUnauthenticatedWriteEndpointExceptLogin() {
        // Login is deliberately absent: IpLoginThrottle covers it on a
        // failure-counting basis, which is the right measure there. Every other
        // unauthenticated write runs a BCrypt hash and must be request-limited.
        assertThat(securityProperties.rateLimits())
                .extracting(SecurityProperties.RateLimit::path)
                .containsExactlyInAnyOrder(
                        "/api/auth/register",
                        "/api/auth/password-reset/request",
                        "/api/auth/password-reset/confirm");
    }

    @Test
    void rateLimitWindowsAndThresholdsAreBound() {
        assertThat(securityProperties.rateLimits())
                .allSatisfy(limit -> {
                    assertThat(limit.maxRequests()).isPositive();
                    assertThat(limit.window()).isNotNull().isPositive();
                    assertThat(limit.name()).isNotBlank();
                });
    }

    @Test
    void transportAndLifetimeSettingsAreBound() {
        assertThat(securityProperties.hstsMaxAge()).isEqualTo(Duration.ofDays(365));
        assertThat(securityProperties.sessionAbsoluteTimeout()).isEqualTo(Duration.ofHours(8));
        assertThat(securityProperties.maxRequestBodySize().toKilobytes()).isEqualTo(64);
        // Dev opts out of transport security; nothing else does.
        assertThat(securityProperties.requireHttps()).isFalse();
    }

    @Test
    void whitelistDoesNotExposeTheH2Console() {
        // The console's unauthenticated access is granted by
        // H2ConsoleSecurityConfig, which is @Profile("dev"), so that the rule
        // and the servlet share one switch. Whitelisting it here would put an
        // unauthenticated full-SQL surface into every profile again — which is
        // what this assertion exists to prevent, since the entry looks
        // harmless next to the others.
        assertThat(securityProperties.whitelist())
                .as("H2 console access must be profile-gated, not whitelisted application-wide")
                .doesNotContain("/h2-console/**");
    }
}
