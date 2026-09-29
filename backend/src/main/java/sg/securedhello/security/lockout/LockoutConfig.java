package sg.securedhello.security.lockout;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.security.ratelimit.LockoutCardinality;
import sg.securedhello.session.SessionTerminationService;
import sg.securedhello.user.UserAccountRepository;

/** The password lockout, its ladder and the NIST cap (ADR-011; ADR-012; ADR-013). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LockoutProperties.class)
public class LockoutConfig {

    @Bean
    LockoutCounter lockoutCounter(LockoutProperties properties) {
        return new LockoutCounter(properties.toLadder(), properties.observationWindow());
    }

    @Bean
    LockoutRecorder lockoutRecorder(UserAccountRepository accounts, PlatformTransactionManager transactionManager,
            LockoutCounter counter, LockoutCardinality cardinality, SessionTerminationService sessions,
            AuditEmitter audit, Clock clock) {
        return new LockoutRecorder(accounts, new TransactionTemplate(transactionManager), counter, cardinality,
                sessions, audit, clock);
    }
}
