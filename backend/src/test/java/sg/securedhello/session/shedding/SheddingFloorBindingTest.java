package sg.securedhello.session.shedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import sg.securedhello.testsupport.Proves;

/** The disk-reserve floor of anonymous-session shedding may be zero, never negative (ADR-041; R-RL-009). */
class SheddingFloorBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SheddingProperties.class)
    static class Binding {
    }

    private static ApplicationContextRunner runner(String floor) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(Binding.class)
                .withPropertyValues("app.security.session.shedding.floor=" + floor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1B", "-256MB"})
    @Proves("T-CFG-036")
    void aNegativeFloorRefusesContextRefresh(String floor) {
        runner(floor).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("must not be negative");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0B", "256MB"})
    @Proves("T-CFG-036")
    void zeroAndThePlanningFloorStart(String floor) {
        runner(floor).run(context -> assertThat(context.getBean(SheddingProperties.class).floor())
                .isEqualTo(DataSize.parse(floor)));
    }
}
