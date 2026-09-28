package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

class CtxDefaultHarnessTest extends CtxDefaultTest {

    @Autowired
    Environment environment;

    @Autowired
    DataSource dataSource;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    Clock injectedClock;

    @Test
    void runsTheDevProfileOnly() {
        assertThat(environment.getActiveProfiles()).containsExactly("dev");
    }

    @Test
    void usesATemporaryH2FileWithTheProductionLockTimeout() throws Exception {
        assertThat(H2Probe.lockTimeoutMillis(dataSource)).isEqualTo(1000);
        H2Probe.assertOnTemporaryFile(dataSource);
    }

    @Test
    void hashesWithTheCheapSharedBcryptCost() {
        assertThat(passwordEncoder.encode("correct horse battery staple")).startsWith("{bcrypt}$2a$04$");
    }

    @Test
    void theOnlyClockIsTheSharedForwardOnlyTestClock() {
        assertThat(injectedClock).isSameAs(clock).isSameAs(TestClock.shared());

        Instant before = injectedClock.instant();
        clock.advance(Duration.ofHours(2));

        assertThat(injectedClock.instant()).isEqualTo(before.plus(Duration.ofHours(2)));
    }
}
