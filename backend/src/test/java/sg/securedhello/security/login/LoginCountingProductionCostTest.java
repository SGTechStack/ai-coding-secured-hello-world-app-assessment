package sg.securedhello.security.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.SecuredHelloApplication;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.security.PasswordEncoderConfig;
import sg.securedhello.security.lockout.LockoutConfig;
import sg.securedhello.security.lockout.LockoutProperties;
import sg.securedhello.security.ratelimit.RateLimitConfig;
import sg.securedhello.session.SessionTerminationService;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CtxNondevTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.time.ClockConfig;
import sg.securedhello.user.PasswordLockoutState;
import sg.securedhello.user.UserAccountRepository;

/**
 * Atomic failure counting at the production BCrypt cost ({@code ctx-nondev}; ADR-067; T-LCK-008). The production
 * configuration, with no profile, refreshes only what counting a wrong password involves: persistence, the encoder at
 * its production cost, the sign-in provider as {@link SignInConfig} builds it, and the lockout recorder with its
 * production constants. What the recorder calls after the count, the session ending and the audit rows, is stubbed:
 * those are other tests' controls, and the stub lets this one count the lock transitions.
 */
class LoginCountingProductionCostTest extends CtxNondevTest {

    private static final int ATTEMPTS = 10;

    @Test
    @Proves("T-LCK-008")
    void tenParallelWrongPasswordsAtCost12LeaveTheAccountAtTheThresholdAndLocked() {
        productionContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        FlywayAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
                        DataJpaRepositoriesAutoConfiguration.class, TransactionAutoConfiguration.class))
                .withUserConfiguration(Persistence.class, ClockConfig.class, PasswordEncoderConfig.class,
                        LockoutConfig.class, RateLimitConfig.class)
                .withBean(SessionTerminationService.class, () -> mock(SessionTerminationService.class))
                .withBean(AuditEmitter.class, () -> mock(AuditEmitter.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
                    Accounts accounts = new Accounts(new JdbcTemplate(context.getBean(DataSource.class)), encoder);
                    assertThat(encoder.encode("any password at all")).as("the production cost")
                            .startsWith("{bcrypt}$2a$12$");
                    Account account = accounts.user();
                    ProviderManager manager = new ProviderManager(new SignInConfig().passwordAuthenticationProvider(
                            context.getBean(UserAccountRepository.class), encoder, context.getBean(Clock.class)));
                    manager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(context));

                    List<AuthenticationException> refusals = race(manager, account.username());

                    int threshold = context.getBean(LockoutProperties.class).threshold();
                    assertThat(refusals).hasSize(ATTEMPTS)
                            .allSatisfy(refusal -> assertThat(refusal).isInstanceOfAny(BadCredentialsException.class,
                                    LockedException.class))
                            .noneMatch(AuthenticationServiceException.class::isInstance);
                    PasswordLockoutState after = accounts.lockoutState(account);
                    assertThat(after.failedLoginAttempts()).as("counted one after another, none lost")
                            .isEqualTo(threshold);
                    assertThat(after.consecutiveFailuresSinceSuccess()).isEqualTo(threshold);
                    assertThat(after.lockedAt(context.getBean(Clock.class).instant())).isTrue();
                    verify(context.getBean(AuditEmitter.class), times(1))
                            .emit(eq(AuditEvent.LOCKOUT_TRIGGERED), any());
                });
    }

    /** Ten wrong-password authentications released at once from one latch; returns how each was refused. */
    private static List<AuthenticationException> race(ProviderManager manager, String username) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        try {
            List<Future<AuthenticationException>> attempts = new ArrayList<>();
            for (int i = 0; i < ATTEMPTS; i++) {
                attempts.add(pool.submit(() -> {
                    start.await();
                    try {
                        manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username,
                                Accounts.WRONG_PASSWORD));
                        return null;
                    } catch (AuthenticationException refused) {
                        return refused;
                    }
                }));
            }
            start.countDown();
            List<AuthenticationException> refusals = new ArrayList<>();
            for (Future<AuthenticationException> attempt : attempts) {
                refusals.add(attempt.get());
            }
            return refusals;
        } finally {
            pool.shutdownNow();
        }
    }

    /** The entities and the account repository, found from the application's packages. */
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = SecuredHelloApplication.class)
    @EnableJpaRepositories(basePackageClasses = UserAccountRepository.class)
    static class Persistence {
    }
}
