package com.sgtechstack.helloworldauthapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
                .containsEntry("ADMIN", List.of("ADMIN_USER_READ", "ADMIN_USER_WRITE"))
                .containsEntry("USER", List.of("HELLO_READ"));
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
                "/api/health",
                "/api/csrf",
                "/api/auth/login",
                "/api/auth/logout",
                "/api/auth/password-reset/request",
                "/api/auth/password-reset/confirm",
                "/h2-console/**"
        );
    }
}
