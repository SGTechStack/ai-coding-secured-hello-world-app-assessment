package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:password-match;DB_CLOSE_DELAY=-1")
@Import(AccountAuthenticationPasswordMatchIT.PasswordEncoderConfiguration.class)
class AccountAuthenticationPasswordMatchIT {

    private static final Instant FUTURE_LOCK = Instant.parse("2100-01-01T00:00:00Z");

    @Autowired
    private AccountAuthenticationService authentication;

    @Autowired
    private AuthenticationAttemptContext authenticationAttempt;

    @Autowired
    private CountingPasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetAccount() {
        authenticationAttempt.clear();
        jdbc.update(
                "UPDATE users SET enabled = TRUE, failed_login_attempts = 0, locked_until = NULL WHERE username = ?",
                "johndoe");
    }

    @AfterEach
    void clearAttemptContext() {
        authenticationAttempt.clear();
    }

    @Test
    void wrongPasswordPerformsExactlyOnePasswordMatch() {
        AccountAuthenticationService.Result result = authenticateAndCount("johndoe", "wrong-password");

        assertThat(result.succeeded()).isFalse();
        assertThat(failedLoginCount()).isEqualTo(1);
    }

    @Test
    void disabledAccountPerformsExactlyOnePasswordMatchBeforeTransactionalRejection() {
        setAccountState(false, 3, null);

        AccountAuthenticationService.Result result = authenticateAndCount("johndoe", "Password123!");

        assertThat(result.succeeded()).isFalse();
        assertThat(failedLoginCount()).isEqualTo(3);
        assertThat(lockedUntil()).isNull();
    }

    @Test
    void lockedAccountPerformsExactlyOnePasswordMatchBeforeTransactionalRejection() {
        setAccountState(true, 5, FUTURE_LOCK);

        AccountAuthenticationService.Result result = authenticateAndCount("johndoe", "Password123!");

        assertThat(result.succeeded()).isFalse();
        assertThat(failedLoginCount()).isEqualTo(5);
        assertThat(lockedUntil()).isEqualTo(FUTURE_LOCK);
    }

    @Test
    void unknownAccountPerformsExactlyOneDummyPasswordMatchWithoutStateMutation() {
        int accountCount = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);

        AccountAuthenticationService.Result result = authenticateAndCount("missing-account", "wrong-password");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.knownAccount()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(accountCount);
        assertThat(failedLoginCount()).isZero();
    }

    private AccountAuthenticationService.Result authenticateAndCount(String username, String password) {
        passwordEncoder.resetMatches();
        authenticationAttempt.start();
        try {
            AccountAuthenticationService.Result result = authentication.authenticate(username, password);
            assertThat(passwordEncoder.matchCount()).isEqualTo(1);
            return result;
        } finally {
            authenticationAttempt.clear();
        }
    }

    private int failedLoginCount() {
        return jdbc.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE username = ?", Integer.class, "johndoe");
    }

    private Instant lockedUntil() {
        Timestamp value = jdbc.queryForObject(
                "SELECT locked_until FROM users WHERE username = ?", Timestamp.class, "johndoe");
        return value == null ? null : value.toInstant();
    }

    private void setAccountState(boolean enabled, int failedCount, Instant lockedUntil) {
        jdbc.update(
                "UPDATE users SET enabled = ?, failed_login_attempts = ?, locked_until = ? WHERE username = ?",
                enabled,
                failedCount,
                lockedUntil == null ? null : Timestamp.from(lockedUntil),
                "johndoe");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PasswordEncoderConfiguration {

        @Bean
        @Primary
        CountingPasswordEncoder countingPasswordEncoder() {
            return new CountingPasswordEncoder(
                    new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder())));
        }
    }

    static final class CountingPasswordEncoder implements PasswordEncoder {

        private final PasswordEncoder delegate;
        private final AtomicInteger matches = new AtomicInteger();

        CountingPasswordEncoder(PasswordEncoder delegate) {
            this.delegate = delegate;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matches.incrementAndGet();
            return delegate.matches(rawPassword, encodedPassword);
        }

        @Override
        public boolean upgradeEncoding(String encodedPassword) {
            return delegate.upgradeEncoding(encodedPassword);
        }

        void resetMatches() {
            matches.set(0);
        }

        int matchCount() {
            return matches.get();
        }
    }
}
