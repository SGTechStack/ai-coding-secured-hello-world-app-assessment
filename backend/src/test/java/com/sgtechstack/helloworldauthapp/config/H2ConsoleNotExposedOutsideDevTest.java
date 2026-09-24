package com.sgtechstack.helloworldauthapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs <strong>without</strong> the {@code dev} profile — the only test class
 * that does — because that is the condition the H2 console fix is about.
 *
 * <p>The console's three concessions (unauthenticated access, CSRF exemption,
 * relaxed frame-options) used to be granted on the single application-wide
 * chain, on the argument that the servlet only exists in dev. The servlet was
 * indeed dev-only; the rules were not. This class pins the difference by
 * booting without dev and asserting the console is unreachable and the
 * concessions absent.
 *
 * <p>Supplies its own datasource because the dev profile is the only one in
 * {@code application.yml} that configures one, and its own admin credentials
 * because the base profile deliberately has no defaults (an unset password
 * there must fail startup).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:h2-console-gating-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "app.admin.username=non-dev-admin",
        "app.admin.password=non-dev-admin-password-1234",
})
@AutoConfigureMockMvc
class H2ConsoleNotExposedOutsideDevTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void theH2ConsoleChainDoesNotExist() {
        assertThat(applicationContext.containsBean("h2ConsoleFilterChain"))
                .as("H2ConsoleSecurityConfig is @Profile(\"dev\"); its chain must not be registered otherwise")
                .isFalse();
    }

    @Test
    void theH2ConsolePathIsDeniedRatherThanWhitelisted() throws Exception {
        // Not merely 404 (servlet absent) — denied by the API chain's terminal
        // denyAll(), because /h2-console/** is no longer in the whitelist. The
        // security filters run ahead of servlet dispatch, so this is the
        // security decision, not a routing miss.
        mockMvc.perform(get("/h2-console/"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/h2-console/login.do"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiResponsesDenyFramingRatherThanAllowingSameOrigin() throws Exception {
        // The old application-wide frameOptions=sameOrigin traded clickjacking
        // protection on every API response for the console's iframe. That
        // relaxation now lives only on the console's own chain.
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
