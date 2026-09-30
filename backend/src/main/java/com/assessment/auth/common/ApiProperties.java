package com.assessment.auth.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalized {@code api.base-path}, resolving to {@code /api/v1} (spec.md S1). */
@ConfigurationProperties(prefix = "api")
public record ApiProperties(String basePath) {}
