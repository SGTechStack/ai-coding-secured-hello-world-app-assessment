package com.assessment.auth.user;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** The role names seeded into the read-only {@code roles} table at startup (spec.md S2). */
@ConfigurationProperties(prefix = "app")
public record RoleSeedProperties(List<String> roles) {}
