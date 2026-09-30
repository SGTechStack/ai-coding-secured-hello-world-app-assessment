package org.eds.demo.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Defines two OpenAPI groups so each channel has its own spec:
 *
 * <ul>
 *   <li>{@code frontend} — {@code /api/**} plus the sign-in endpoint {@code /login}, consumed by
 *       the SPA's generated client.
 *   <li>{@code admin} — {@code /admin/api/**}, consumed by the separately deployed admin frontend.
 *       Kept outside {@code /api/**} so admin operations never appear in the public spec.
 * </ul>
 *
 * <p>Gated on {@code unsafe-openapi} alongside {@link OpenApiSecurityConfiguration}.
 */
@Configuration
@Profile("unsafe-openapi")
class OpenApiGroupConfiguration {

  @Bean
  GroupedOpenApi frontendApiGroup() {
    return GroupedOpenApi.builder()
        .group("frontend")
        .pathsToMatch("/api/**", SecurityConfiguration.LOGIN_URL)
        .build();
  }

  @Bean
  GroupedOpenApi adminApiGroup() {
    return GroupedOpenApi.builder().group("admin").pathsToMatch("/admin/api/**").build();
  }
}
