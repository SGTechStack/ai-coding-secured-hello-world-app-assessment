package com.assessment.securedhelloworld.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the H2 console is denied outright when the {@code dev}
 * profile is not active, even if {@code spring.h2.console.enabled} were
 * ever accidentally set to {@code true} in a non-dev profile — the
 * {@code SecurityFilterChain} itself enforces the profile gate, not just
 * the H2 autoconfiguration toggle in {@code application-dev.yml}.
 *
 * <p>Uses a synthetic non-dev profile ({@code "not-dev"}) with an inline
 * H2 datasource (since the real datasource config lives only under the
 * {@code dev} profile) so the application context can still boot while
 * exercising the "dev profile not active" branch of the security config.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("not-dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:h2consoleprofiletest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.h2.console.enabled=true"
})
class H2ConsoleProfileGateTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void h2ConsoleIsDeniedWhenDevProfileIsNotActiveEvenIfEnabledPropertyIsTrue() throws Exception {
        // denyAll() on an unauthenticated request surfaces as 401 (the
        // custom unauthorizedEntryPoint) rather than 403, since Spring
        // Security asks for authentication before it evaluates whether
        // an authenticated caller would have been authorized. Either way
        // the console is unreachable, which is what this test asserts.
        mockMvc.perform(get("/h2-console/"))
                .andExpect(status().isUnauthorized());
    }
}
