package sg.securedhello.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.password.PasswordPolicy.Account;
import sg.securedhello.security.PasswordEncoderConfig;
import sg.securedhello.testsupport.CtxNondevTest;
import sg.securedhello.testsupport.Proves;

/**
 * The policy and the encoder as production binds them (ctx-nondev): the bounds from {@code application.yml}, the two
 * pinned lists from the classpath, and the BCrypt cost.
 */
class PasswordPolicyConfigTest extends CtxNondevTest {

    private static final Account ACCOUNT = new Account("jane.tester", "jtmail@example.test");

    @Test
    void theProductionPolicyBoundsAreTheSpecsValues() {
        assertThat(productionProperty("app.security.password.min-length", Integer.class)).isEqualTo(15);
        assertThat(productionProperty("app.security.password.max-bytes", Integer.class)).isEqualTo(72);
        assertThat(productionProperty("app.security.password.min-strength-score", Integer.class)).isEqualTo(3);
        assertThat(productionProperty("app.security.password.history-length", Integer.class)).isEqualTo(3);
    }

    @Test
    @Proves("T-CRED-025")
    void theProductionEncoderIsBcryptAtCostTwelve() {
        productionContextRunner().withUserConfiguration(PasswordEncoderConfig.class).run(context ->
                assertThat(context.getBean(PasswordEncoder.class).encode("a passphrase to encode"))
                        .startsWith("{bcrypt}$2a$12$"));
    }

    @Test
    void theBuiltPolicyUsesThePinnedListsAndTheServiceName() {
        productionContextRunner().withUserConfiguration(PasswordEncoderConfig.class, PasswordPolicyConfig.class)
                .run(context -> {
                    PasswordPolicy policy = context.getBean(PasswordPolicy.class);
                    assertThat(policy.check("QwertyUiopAsdfGh", ACCOUNT, candidate -> false))
                            .as("a breach-slice entry").contains(PasswordRule.BLOCKLISTED);
                    assertThat(policy.check("my sghello garden gnome", ACCOUNT, candidate -> false))
                            .as("a context word").contains(PasswordRule.CONTEXT_TERM);
                    assertThat(policy.check("Secured Hello World forever", ACCOUNT, candidate -> false))
                            .as("the service name").contains(PasswordRule.CONTEXT_TERM);
                    assertThat(policy.check("velvet harbour quietly hums", ACCOUNT, candidate -> false)).isEmpty();
                });
    }

    @Test
    @Proves("T-CRED-004")
    void theStrengthThresholdIsReadFromConfiguration() {
        // "football football" scores exactly 3: accepted at the production threshold, refused at 4.
        productionContextRunner().withUserConfiguration(PasswordEncoderConfig.class, PasswordPolicyConfig.class)
                .run(context -> assertThat(context.getBean(PasswordPolicy.class)
                        .check("football football", ACCOUNT, candidate -> false)).isEmpty());
        productionContextRunner().withUserConfiguration(PasswordEncoderConfig.class, PasswordPolicyConfig.class)
                .withPropertyValues("app.security.password.min-strength-score=4")
                .run(context -> assertThat(context.getBean(PasswordPolicy.class)
                        .check("football football", ACCOUNT, candidate -> false)).contains(PasswordRule.TOO_WEAK));
    }

    @Test
    void theListsSkipCommentsAndBlankLinesAndEveryEntryIsFifteenCharactersOrMore() {
        assertThat(PasswordPolicyConfig.entries(PasswordPolicyConfig.BREACH_SLICE))
                .isNotEmpty()
                .contains("correcthorsebatterystaple")
                .allSatisfy(entry -> assertThat(entry).hasSizeGreaterThanOrEqualTo(15).doesNotStartWith("#"));
        assertThat(PasswordPolicyConfig.entries(PasswordPolicyConfig.CONTEXT_WORDS))
                .containsExactly("securedhello", "helloworld", "sghello");
    }
}
