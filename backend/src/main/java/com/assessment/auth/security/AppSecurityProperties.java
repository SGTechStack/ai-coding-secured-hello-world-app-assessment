package com.assessment.auth.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chain-level security configuration (spec.md S3, S4).
 *
 * @param allowedOrigins an explicit allowlist. Wildcards are impossible here by construction, which
 *     matters because {@code allowCredentials=true} makes a wildcard origin a credential leak.
 * @param responseTimeFloor minimum wall-clock duration for login and password-reset-request. A
 *     <strong>partial</strong> discharge of Std:247's identical-timing clause — assert the bound,
 *     do not claim constant time (spec.md S7).
 * @param authorizationMatrix flat, ordered, first match wins, terminal {@code denyAll}. The
 *     <strong>sole</strong> authorization mechanism: {@code @EnableMethodSecurity} is off and no
 *     {@code @PreAuthorize} exists anywhere in this codebase.
 */
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
    List<String> allowedOrigins, Duration responseTimeFloor, List<MatrixRow> authorizationMatrix) {

  /**
   * One row of the matrix.
   *
   * @param method an HTTP method name, or {@code ANY} for "any method"
   * @param path an Ant-style path. <strong>No bare single-segment wildcards, ever</strong> —
   *     RBAC:43's {@code /api/v1/users/*} would have granted batch reset while denying
   *     {@code /{userId}/status}, {@code /role} and {@code /resetPassword}.
   * @param access {@code permitAll}, {@code authenticated}, {@code denyAll} or
   *     {@code hasRole('NAME')}
   */
  public record MatrixRow(String method, String path, String access) {}
}
