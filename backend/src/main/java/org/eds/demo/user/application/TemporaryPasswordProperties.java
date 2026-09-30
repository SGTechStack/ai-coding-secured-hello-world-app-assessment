package org.eds.demo.user.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Temporary Password policy, bound under {@code app.temporary-password}.
 *
 * @param ttl how long an unchanged Temporary Password keeps working after it is issued
 */
@ConfigurationProperties(prefix = "app.temporary-password")
public record TemporaryPasswordProperties(@DefaultValue("24h") Duration ttl) {}
