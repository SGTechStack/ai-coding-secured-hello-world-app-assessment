package sg.securedhello.audit;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import sg.securedhello.config.ApplicationKeys;
import sg.securedhello.security.source.ClientIpProperties;
import sg.securedhello.security.source.SourceKeyResolver;

/**
 * The audit emitter, keyed by the log HMAC key (ADR-054), and the lifecycle rows. Scheduling is enabled for the
 * keying window's periodic close ({@link AuditLifecycleRows}).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AuditProperties.class)
public class AuditConfig {

    @Bean
    AuditEmitter auditEmitter(ApplicationKeys keys, SourceKeyResolver sourceKeyResolver, AuditProperties properties,
            Clock clock) {
        LogFieldHasher hasher = new LogFieldHasher(keys.logHmac().bytes());
        return new AuditEmitter(new AuditRequestFields(sourceKeyResolver::resolve, hasher,
                properties.urlPath().maxLength()), clock, properties.keying().window(),
                properties.truncation().distinctSources(), properties.truncation().distinctUsers());
    }

    @Bean
    AuditLifecycleRows auditLifecycleRows(AuditEmitter emitter, Environment environment, ClientIpProperties clientIp,
            ApplicationKeys keys, LoggingSystem loggingSystem) {
        return new AuditLifecycleRows(emitter, environment, clientIp, keys, loggingSystem);
    }
}
