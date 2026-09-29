package sg.securedhello.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@code app.security.password.*} is refused at startup when a value would weaken ADR-001, ADR-002, ADR-003 or ADR-005,
 * or would fail only at runtime: a history of 0 throws on every password set, and more than 72 bytes reaches BCrypt,
 * which throws. Binding checks BCrypt's own cost range in every posture; the non-dev floor of 12 is checked by
 * {@link PasswordEncoderConfig} on the bound value, so the harness's cost 4 runs only under {@code dev}.
 */
class PasswordPropertiesTest {

    private static final String PREFIX = "app.security.password.";

    @ParameterizedTest
    @CsvSource({"history-length, 0", "history-length, -1", "max-bytes, 73", "max-bytes, 0", "max-bytes, 14",
            "min-length, 14", "min-length, 0", "min-strength-score, 2", "min-strength-score, 5",
            "bcrypt-strength, 3", "bcrypt-strength, 32"})
    void aValueOutsideItsBoundRefusesStartup(String property, String value) {
        runner().withPropertyValues(PREFIX + property + "=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().isInstanceOf(BindValidationException.class)
                    .hasMessageContaining(PREFIX);
        });
    }

    @ParameterizedTest
    @CsvSource({"history-length, 1", "max-bytes, 72", "max-bytes, 15", "min-length, 15", "min-length, 64",
            "min-strength-score, 3", "min-strength-score, 4", "bcrypt-strength, 12", "bcrypt-strength, 31"})
    void aValueOnItsBoundStarts(String property, String value) {
        runner().withPropertyValues(PREFIX + property + "=" + value).run(context -> assertThat(context)
                .hasNotFailed().hasSingleBean(PasswordProperties.class));
    }

    @Test
    void aMinimumLengthAboveTheByteCeilingRefusesStartup() {
        runner().withPropertyValues(PREFIX + "min-length=40", PREFIX + "max-bytes=39").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("max-bytes must be at least");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"4", "11", "+4", "0x4", "#4", "1 1", " 4 "})
    void outsideDevABcryptCostBelowTwelveInAnySpellingBootConvertsRefusesStartup(String cost) {
        runner().withPropertyValues(PREFIX + "bcrypt-strength=" + cost).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("app.security.password.bcrypt-strength is below 12");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "13"})
    void outsideDevABcryptCostOfTwelveOrMoreStarts(String cost) {
        runner().withPropertyValues(PREFIX + "bcrypt-strength=" + cost)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(PasswordEncoder.class));
    }

    @Test
    void theFloorIsCheckedAtRefreshEvenUnderLazyInitialization() {
        runner().withPropertyValues(PREFIX + "bcrypt-strength=4")
                .withInitializer(context -> context.addBeanFactoryPostProcessor(
                        new LazyInitializationBeanFactoryPostProcessor()))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void underDevTheTestSpeedBcryptCostStarts() {
        runner().withPropertyValues(PREFIX + "bcrypt-strength=4", "spring.profiles.active=dev")
                .run(context -> assertThat(context.getBean(PasswordEncoder.class).encode("a passphrase to encode"))
                        .startsWith("{bcrypt}$2a$04$"));
    }

    /** The production policy values, as {@code application.yml} commits them, no profile; each case overrides one. */
    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(PasswordEncoderConfig.class)
                .withPropertyValues(PREFIX + "bcrypt-strength=12", PREFIX + "min-length=15", PREFIX + "max-bytes=72",
                        PREFIX + "min-strength-score=3", PREFIX + "history-length=3");
    }
}
