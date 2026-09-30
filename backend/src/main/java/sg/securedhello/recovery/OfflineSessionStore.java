package sg.securedhello.recovery;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The session store without a web server. Boot configures Spring Session only in a web application, and the runner
 * runs with {@code web-application-type=none} (ADR-072); it still ends the sessions of an account it rebinds, so it
 * gets the same JDBC repository over the same tables and attribute allowlist.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnNotWebApplication
class OfflineSessionStore {

    @Bean
    JdbcIndexedSessionRepository sessionRepository(JdbcTemplate jdbc, PlatformTransactionManager transactions,
            @Qualifier("springSessionConversionService") ConversionService conversions) {
        // REQUIRES_NEW, as Spring Session's own configuration sets it: a session kill runs after the runner's commit,
        // and must not join the finished transaction (SessionTerminationService).
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        JdbcIndexedSessionRepository repository = new JdbcIndexedSessionRepository(jdbc, template);
        repository.setConversionService(conversions);
        return repository;
    }
}
