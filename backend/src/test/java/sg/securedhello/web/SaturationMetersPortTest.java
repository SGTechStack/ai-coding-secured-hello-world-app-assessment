package sg.securedhello.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CtxPortTest;
import sg.securedhello.testsupport.PortSession;
import sg.securedhello.testsupport.Proves;

/**
 * The request thread pool is pinned at its configured size, and its saturation and the connection pool's are metered
 * (TM-07; ASVS 6.1.1 (L1)): after a password sign-in on a real port, Tomcat's thread meters and Hikari's pool meters
 * exist and have moved.
 */
class SaturationMetersPortTest extends CtxPortTest {

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private double gauge(String name) {
        Gauge gauge = registry.find(name).gauge();
        assertThat(gauge).as(name).isNotNull();
        return gauge.value();
    }

    @Test
    @Proves("T-OBS-005")
    void tomcatAndHikariSaturationMetersExistAndMoveAfterAPasswordSignIn() {
        Account account = new Accounts(jdbc, passwordEncoder).user();
        PortSession signedIn = PortSession.bootstrap(restClient).signIn(account.username(), account.password());
        assertThat(signedIn.get("/api/profile").getStatus().value()).isEqualTo(200);

        assertThat(environment.getProperty("server.tomcat.threads.max", Integer.class)).isEqualTo(200);
        assertThat(gauge("tomcat.threads.config.max")).isEqualTo(200);
        assertThat(gauge("tomcat.threads.current")).isPositive();
        assertThat(registry.find("tomcat.threads.busy").gauge()).isNotNull();
        assertThat(registry.find("hikaricp.connections.acquire").timers()).isNotEmpty()
                .allSatisfy(timer -> assertThat(timer.count()).isPositive());
        assertThat(registry.find("hikaricp.connections.usage").timers()).extracting(Timer::count)
                .anySatisfy(count -> assertThat(count).isPositive());
        assertThat(registry.find("hikaricp.connections.pending").gauge()).isNotNull();
    }
}
