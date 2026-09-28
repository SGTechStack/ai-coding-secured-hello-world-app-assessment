package sg.securedhello.audit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import sg.securedhello.config.ApplicationKeys;
import sg.securedhello.security.source.ClientIpProperties;
import sg.securedhello.security.source.SourceKeyResolver;

/** The audit emitter, keyed by the log HMAC key (ADR-054), and the lifecycle rows. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditConfig {

    @Bean
    AuditEmitter auditEmitter(ApplicationKeys keys, SourceKeyResolver sourceKeyResolver, AuditProperties properties) {
        LogFieldHasher hasher = new LogFieldHasher(keys.logHmac().bytes());
        return new AuditEmitter(new AuditRequestFields(sourceKeyResolver::resolve, hasher,
                properties.urlPath().maxLength()));
    }

    @Bean
    AuditLifecycleRows auditLifecycleRows(AuditEmitter emitter, Environment environment, ClientIpProperties clientIp,
            ApplicationKeys keys, LoggingSystem loggingSystem) {
        return new AuditLifecycleRows(emitter, environment, clientIp, keys, loggingSystem);
    }
}
