package sg.securedhello.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.security.ratelimit.RateLimitProperties;
import sg.securedhello.security.ratelimit.RateLimitProperties.Budget;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxBudgetTest;
import sg.securedhello.testsupport.PasswordResets;
import sg.securedhello.testsupport.Proves;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The reset routes on their tabled budgets (ADR-010): per source on both, and per submitted address on the request,
 * where the address's budget is spent the same way whether or not it names an account.
 */
class PasswordResetBudgetTest extends CtxBudgetTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RateLimitProperties budgets;

    private String requestFromFreshSource(String email) throws Exception {
        return new PasswordResets(mockMvc, nextSource()).request(email).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @Proves("T-RL-004")
    void anAddressBeyondItsBudgetIsRefusedAndSentNothingWhetherOrNotItIsRegisteredUntilItRefills() throws Exception {
        Budget budget = budgets.passwordResetRequest().identifier();
        String registered = PasswordResets.emailOf(new Accounts(jdbc, passwordEncoder).user());
        String unregistered = Accounts.unknownUsername() + "@example.test";
        for (long i = 0; i < budget.burst(); i++) {
            for (String email : new String[] {registered, unregistered}) {
                new PasswordResets(mockMvc, nextSource()).request(email).andExpect(status().isAccepted());
            }
        }
        assertThat(emails.to(registered)).hasSize((int) budget.burst());

        clearInvocations(userAccounts);
        try (AuditCapture audit = AuditCapture.start()) {
            String refusedRegistered = requestFromFreshSource(registered);
            String refusedUnregistered = requestFromFreshSource(unregistered);

            assertThat(withoutTraceId(refusedRegistered)).isEqualTo(withoutTraceId(refusedUnregistered));
            assertThat(JSON.readTree(refusedRegistered).get("code").asString())
                    .isEqualTo(ErrorCode.TOO_MANY_REQUESTS.name());
            assertThat(audit.withMessage("Request throttled for its submitted identifier.")).hasSize(2)
                    .allSatisfy(row -> assertThat(row).doesNotContainKey("user.id"));
        }
        verify(userAccounts, never()).findByEmail(anyString());
        assertThat(emails.to(registered)).as("no further email").hasSize((int) budget.burst());

        clock.advance(budget.refillPeriod());
        new PasswordResets(mockMvc, nextSource()).request(registered).andExpect(status().isAccepted());
        new PasswordResets(mockMvc, nextSource()).request(registered).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andExpect(header().exists("Retry-After"));
        assertThat(emails.to(registered)).hasSize((int) budget.burst() + 1);
    }

    @Test
    void theRequestSpendsItsPerSourceBudget() throws Exception {
        PasswordResets resets = new PasswordResets(mockMvc, nextSource());
        for (long i = 0; i < budgets.passwordResetRequest().source().burst(); i++) {
            resets.request(Accounts.unknownUsername() + "@example.test").andExpect(status().isAccepted());
        }

        resets.request(Accounts.unknownUsername() + "@example.test").andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void theConfirmationSpendsItsPerSourceBudget() throws Exception {
        PasswordResets resets = new PasswordResets(mockMvc, nextSource());
        for (long i = 0; i < budgets.passwordResetConfirm().source().burst(); i++) {
            resets.confirm("F".repeat(43), PasswordResets.NEW_PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        }

        resets.confirm("F".repeat(43), PasswordResets.NEW_PASSWORD).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
    }

    private static ObjectNode withoutTraceId(String body) {
        ObjectNode node = (ObjectNode) JSON.readTree(body);
        node.remove("traceId");
        return node;
    }
}
