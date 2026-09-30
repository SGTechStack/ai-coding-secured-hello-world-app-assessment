package com.example.hello.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Security tunables bound from {@code app.security.*}. Defaults are the hardened
 * production values; the dev profile relaxes only what local HTTP needs.
 */
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
    @DefaultValue("true") boolean cookieSecure,
    @DefaultValue("Lax") String cookieSameSite,
    @DefaultValue("PT30M") Duration sessionTimeout,
    @DefaultValue List<String> allowedOrigins,
    @DefaultValue Login login) {

  /** Brute-force controls bound from {@code app.security.login.*}. */
  public record Login(
      @DefaultValue("5") int maxFailedAttempts,
      @DefaultValue("PT15M") Duration lockoutDuration,
      @DefaultValue("20") int ipMaxFailures,
      @DefaultValue("PT15M") Duration ipWindow) {}
}
