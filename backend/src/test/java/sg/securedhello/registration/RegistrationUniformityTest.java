package sg.securedhello.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import sg.securedhello.user.Tombstones;

/**
 * Registration is uniform across the email axis (ADR-032; R-CRED-018), and runs on its tabled per-source budgets. It
 * runs on {@link CtxBudgetTest}: counting encoder calls needs the spied encoder, and the budgets are the real ones.
 */
class RegistrationUniformityTest extends CtxBudgetTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RateLimitProperties budgets;

    @Autowired
    private Tombstones tombstones;

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

    /** Status and body, the body without its per-request trace id. */
    private String probeStep(Registrations registrations, String username, String email) throws Exception {
        MvcResult result = registrations.register(username, email).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return result.getResponse().getStatus() + " " + body.replaceAll("\"traceId\":\"[0-9a-f]*\"", "");
    }

    /**
     * The two-request probe (review 14-16 H1): register a fresh username with the victim's address, then the same
     * username with the attacker's. Every email state must answer both steps identically.
     */
    private List<String> probe(String victimEmail) throws Exception {
        Registrations registrations = new Registrations(mockMvc, nextSource());
        String username = Registrations.freshUsername();
        return List.of(probeStep(registrations, username, victimEmail),
                probeStep(registrations, username, Registrations.emailFor(Registrations.freshUsername())));
    }

    @Test
    @Proves("T-AUTH-018")
    void theTwoRequestUsernameProbeAnswersAlikeInEveryEmailState() throws Exception {
        String newAddress = Registrations.emailFor(Registrations.freshUsername());

        String pendingUser = Registrations.freshUsername();
        String pendingAddress = Registrations.emailFor(pendingUser);
        new Registrations(mockMvc, nextSource()).register(pendingUser, pendingAddress).andReturn();

        String activatedAddress = new Accounts(jdbc, passwordEncoder).user().username() + "@example.test";

        String invited = Registrations.freshUsername();
        String invitedAddress = Registrations.emailFor(invited);
        jdbc.update("INSERT INTO users (id, username, email, role, enabled, created_at) VALUES (?, ?, ?, 'ADMIN', TRUE, ?)",
                UUID.randomUUID(), invited, invitedAddress, Timestamp.from(clock.instant()));

        String tombstonedAddress = Registrations.emailFor(Registrations.freshUsername());
        jdbc.update("INSERT INTO deleted_users (user_id, username, email_hmac, deleted_at, deleted_by_id)"
                        + " VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), Registrations.freshUsername(),
                tombstones.emailHmac(tombstonedAddress), Timestamp.from(clock.instant()), UUID.randomUUID());

        Map<String, List<String>> answers = new LinkedHashMap<>();
        answers.put("new", probe(newAddress));
        answers.put("pending", probe(pendingAddress));
        answers.put("activated", probe(activatedAddress));
        answers.put("invited", probe(invitedAddress));
        answers.put("tombstoned", probe(tombstonedAddress));

        List<String> expected = answers.get("new");
        assertThat(expected.get(0)).startsWith("202");
        assertThat(expected.get(1)).startsWith("400").contains("USERNAME_UNAVAILABLE");
        assertThat(answers).allSatisfy((state, answer) -> assertThat(answer).as(state).isEqualTo(expected));
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
