package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.security.PasswordEncoderConfig;

class CtxNondevHarnessTest extends CtxNondevTest {

    @Test
    void productionConfigurationHasNoActiveProfile() {
        assertThat(productionEnvironment().getActiveProfiles()).isEmpty();
    }

    @Test
    void productionServesOnPort8080() {
        assertThat(productionProperty("server.port", Integer.class)).isEqualTo(8080);
    }

    @Test
    void productionDatasourcePinsTheLockTimeout() {
        assertThat(productionProperty("spring.datasource.url", String.class))
                .startsWith("jdbc:h2:file:")
                .contains(";LOCK_TIMEOUT=1000");
    }

    @Test
    void productionHashesAtBcryptCost12WithoutRefreshingTheApplication() {
        productionContextRunner()
                .withUserConfiguration(PasswordEncoderConfig.class)
                .run(context -> assertThat(context.getBean(PasswordEncoder.class)
                        .encode("correct horse battery staple")).startsWith("{bcrypt}$2a$12$"));
    }

    @Test
    void theRunnerIsOnATemporaryH2File() {
        productionContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .run(context -> H2Probe.assertOnTemporaryFile(context.getBean(DataSource.class)));
    }
}
