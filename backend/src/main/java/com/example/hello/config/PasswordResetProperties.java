package com.example.hello.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Password-reset tunables bound from {@code app.password-reset.*}. */
@ConfigurationProperties(prefix = "app.password-reset")
public record PasswordResetProperties(
    @DefaultValue("PT20M") Duration tokenTtl,
    @DefaultValue("https://example.invalid/reset-password") String resetUrlBase) {}
