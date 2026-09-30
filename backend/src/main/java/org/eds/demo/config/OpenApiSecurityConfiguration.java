package org.eds.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Exposes OpenAPI documentation only for non-production development profiles.
 *
 * <p>Spring Security protects every route by default. This profile-scoped configuration permits
 * Swagger UI and the generated OpenAPI document for local, dev, and test environments while keeping
 * the normal authentication requirement for application endpoints.
 */
@Configuration
@Profile("unsafe-openapi")
class OpenApiSecurityConfiguration {

  @Bean
  @Order(SecurityFilterChainOrder.OPEN_API)
  SecurityFilterChain openApiSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.securityMatcher("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .build();
  }
}
