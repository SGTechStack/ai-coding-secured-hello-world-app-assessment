package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.helloauth.service.AdminBootstrap;
import com.example.helloauth.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 12's failure modes.
 *
 * <p>An admin account nobody asked for is worse than no admin account, so a configured password that
 * is missing or too weak has to result in nothing being created — not in a seeded admin with a
 * password the application would refuse from an ordinary visitor.
 */
@TestPropertySource(properties = {"app.admin.username=seeded-admin", "app.admin.password=short"})
class AdminBootstrapGuardsTest extends ApiIntegrationTest {

    @Autowired private AdminBootstrap adminBootstrap;

    @Test
    @DisplayName("a configured password that fails the policy seeds nothing")
    void refusesToSeedWithAWeakPassword() {
        adminBootstrap.run(null);

        assertThat(accounts.count()).isZero();
    }
}
