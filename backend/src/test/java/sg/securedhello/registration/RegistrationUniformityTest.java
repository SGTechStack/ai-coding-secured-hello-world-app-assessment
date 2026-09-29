package sg.securedhello.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.security.ratelimit.RateLimitProperties;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.CtxBudgetTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;

/**
 * Registration is uniform across the email axis (ADR-032; R-CRED-018), and runs on its tabled per-source budgets. It
 * runs on {@link CtxBudgetTest}: counting encoder calls needs the spied encoder, and the budgets are the real ones.
 */
class RegistrationUniformityTest extends CtxBudgetTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RateLimitProperties budgets;

    /** One registration's wire answer: status and body, and the encoder calls it cost. */
    private record Answer(int status, String body, long encoderCalls) {
    }

    private Answer register(String username, String email) throws Exception {
        Registrations registrations = new Registrations(mockMvc, nextSource());
        clearInvocations(passwordEncoder);
        MvcResult result = registrations.register(username, email).andReturn();
        long calls = mockingDetails(passwordEncoder).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().matches("encode|matches"))
                .count();
        return new Answer(result.getResponse().getStatus(),
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), calls);
    }

    @Test
    @Proves("T-AUTH-014")
    void aNewAPendingAndAnActivatedAddressGetTheIdenticalAnswerAndNoPasswordWork() throws Exception {
        String newUser = Registrations.freshUsername();
        Answer fresh = register(newUser, Registrations.emailFor(newUser));

        Answer pending = register(Registrations.freshUsername(), Registrations.emailFor(newUser));

        Accounts.Account activated = new Accounts(jdbc, passwordEncoder).user();
        Answer existing = register(Registrations.freshUsername(), activated.username() + "@example.test");

        assertThat(Map.of("new", fresh, "pending", pending, "activated", existing)).allSatisfy((state, answer) -> {
            assertThat(answer.status()).as(state).isEqualTo(202);
            assertThat(answer.body()).as(state).isEqualTo(fresh.body());
            assertThat(answer.encoderCalls()).as("%s: no PasswordEncoder encode or matches", state).isZero();
        });
    }

    @Test
    void registrationSpendsItsPerSourceBudget() throws Exception {
        Registrations registrations = new Registrations(mockMvc, nextSource());
        long burst = budgets.register().source().burst();
        for (long i = 0; i < burst; i++) {
            String username = Registrations.freshUsername();
            assertThat(registrations.register(username, Registrations.emailFor(username)).andReturn().getResponse()
                    .getStatus()).isEqualTo(202);
        }

        String username = Registrations.freshUsername();
        registrations.register(username, Registrations.emailFor(username))
                .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS)).andExpect(header().exists("Retry-After"));
    }

    @Test
    void activationSpendsItsPerSourceBudget() throws Exception {
        Registrations registrations = new Registrations(mockMvc, nextSource());
        long burst = budgets.registerActivate().source().burst();
        for (long i = 0; i < burst; i++) {
            registrations.activate("C".repeat(43), Registrations.PASSWORD)
                    .andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        }

        registrations.activate("C".repeat(43), Registrations.PASSWORD).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
    }
}
