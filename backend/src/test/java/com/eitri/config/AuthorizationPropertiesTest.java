package com.eitri.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.auth.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

class AuthorizationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues(
                    "app.security.authorization.matrix[0].role=USER",
                    "app.security.authorization.matrix[0].permissions[0].method=GET",
                    "app.security.authorization.matrix[0].permissions[0].path=/api/v1/auth/me",
                    "app.security.authorization.matrix[1].role=ADMIN",
                    "app.security.authorization.matrix[1].permissions[0].method=GET",
                    "app.security.authorization.matrix[1].permissions[0].path=/api/v1/auth/me");

    @Test
    void matrixBindsToTheRoleEnumAndGroupsEqualRequests() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            AuthorizationProperties properties = context.getBean(AuthorizationProperties.class);
            assertThat(properties.matrix()).extracting(AuthorizationProperties.RoleAccess::role)
                    .containsExactly(Role.USER, Role.ADMIN);
            assertThat(properties.rolesByRequest())
                    .containsExactly(org.assertj.core.api.Assertions.entry(
                            new AuthorizationProperties.RequestPermission(HttpMethod.GET, "/api/v1/auth/me"),
                            java.util.List.of(Role.USER, Role.ADMIN)));
        });
    }

    @Test
    void matrixRoleOutsideTheEnumStopsStartup() {
        contextRunner
                .withPropertyValues("app.security.authorization.matrix[0].role=USER_MANAGER")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("USER_MANAGER");
                });
    }

    @Test
    void emptyMatrixStopsStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthorizationProperties.class)
    static class PropertiesConfiguration {}
}
