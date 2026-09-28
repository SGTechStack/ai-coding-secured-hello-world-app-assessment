package sg.securedhello.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

class ConfigurationValidatorPresenceTest extends CtxDefaultTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @Proves("T-CFG-033")
    void theContextContainsTheRefreshPhaseProhibitedConfigurationValidator() {
        assertThat(context.getBeansOfType(ProhibitedConfigurationValidator.class)).hasSize(1);
    }

    @Test
    void theContextHoldsTheThreeValidatedKeysAndTheResetLinkGuard() {
        assertThat(context.getBean(ApplicationKeys.class).all()).hasSize(3);
        assertThat(context.getBeansOfType(ResetLinkLoggerGuard.class)).hasSize(1);
    }
}
