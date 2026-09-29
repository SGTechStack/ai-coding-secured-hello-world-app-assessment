package sg.securedhello.security.login;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

import java.time.Clock;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.user.UserAccountRepository;

import tools.jackson.databind.json.JsonMapper;

/** Password sign-in and sign-out (ADR-033; ADR-038): the provider, and the pieces {@link SignIn} adds to the chain. */
@Configuration(proxyBeanMethods = false)
public class SignInConfig {

    /**
     * The one password provider. {@code alwaysPerformAdditionalChecksOnUser} keeps its default, {@code true}, so a
     * disabled account still costs one {@code matches()} (REJ-005; T-AUTH-005). No {@code CompromisedPasswordChecker}
     * is set, and none is a bean (T-AUTH-013).
     */
    @Bean
    DaoAuthenticationProvider passwordAuthenticationProvider(UserAccountRepository accounts,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new AccountUserDetailsService(accounts));
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    SignIn signIn(DaoAuthenticationProvider provider, AuthenticationEventPublisher events,
            FindByIndexNameSessionRepository<? extends Session> sessions, AuditEmitter audit,
            ProblemDetailWriter writer, JsonMapper jsonMapper, Clock clock) {
        return new SignIn(provider, events, new SpringSessionBackedSessionRegistry<>(sessions), audit, writer,
                jsonMapper, clock);
    }

    @Bean
    LoginFailureAudit loginFailureAudit(UserAccountRepository accounts, AuditEmitter audit) {
        return new LoginFailureAudit(accounts, audit);
    }
}
