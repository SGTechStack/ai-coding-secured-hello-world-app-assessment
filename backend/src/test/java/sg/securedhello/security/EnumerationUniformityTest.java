package sg.securedhello.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.PasswordResets;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.user.UserAccount;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Enumeration safety by literal equality (ADR-032; ADR-033): over every account state, each of the three endpoints that
 * take an identifier answers one exact status and body. Only {@code traceId} may vary, and {@code instance} is the
 * endpoint's constant, never anything the caller sent.
 */
class EnumerationUniformityTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The one sign-in refusal, without its {@code traceId}. */
    private static final String LOGIN_REFUSAL = "{\"type\":\"tag:securedhello.sg,2026:problem:authentication-failed\","
            + "\"title\":\"Authentication failed\",\"status\":401,"
            + "\"detail\":\"Authentication is required, or the credentials were not accepted.\","
            + "\"instance\":\"/api/login\",\"code\":\"AUTHENTICATION_FAILED\"}";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
    }

    private static String email(String username) {
        return username + "@example.test";
    }

    /** Each account state, as the username and password a sign-in submits; the address is the username's. */
    private Map<String, String[]> states() {
        Account known = accounts.user();
        Account locked = accounts.user();
        jdbc.update("UPDATE users SET locked_until = ? WHERE id = ?",
                Timestamp.from(clock.instant().plus(Duration.ofHours(1))), locked.id());
        Account capped = accounts.user();
        jdbc.update("UPDATE users SET password_disabled_at = ? WHERE id = ?", Timestamp.from(clock.instant()),
                capped.id());
        Account expired = accounts.user();
        jdbc.update("UPDATE users SET force_password_change = TRUE, credential_issued_at = ? WHERE id = ?",
                Timestamp.from(clock.instant().minus(UserAccount.FORCED_CHANGE_GRACE).minusSeconds(1)),
                expired.id());
        Map<String, String[]> states = new LinkedHashMap<>();
        states.put("unknown", new String[] {Accounts.unknownUsername(), Accounts.PASSWORD});
        states.put("wrong password", new String[] {known.username(), Accounts.WRONG_PASSWORD});
        states.put("locked", new String[] {locked.username(), Accounts.PASSWORD});
        states.put("disabled", new String[] {accounts.disabled().username(), Accounts.PASSWORD});
        states.put("grace-period-disabled (NIST cap)", new String[] {capped.username(), Accounts.PASSWORD});
        states.put("not yet activated", new String[] {accounts.notActivated().username(), Accounts.PASSWORD});
        states.put("forced-change expiry, correct password", new String[] {expired.username(), Accounts.PASSWORD});
        states.put("forced-change expiry, wrong password", new String[] {expired.username(), Accounts.WRONG_PASSWORD});
        return states;
    }

    private static String answer(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (!body.isEmpty()) {
            ObjectNode problem = (ObjectNode) JSON.readTree(body);
            assertThat(problem.get("traceId").asString()).isNotBlank();
            problem.remove("traceId");
            body = problem.toString();
        }
        return result.getResponse().getStatus() + " " + body;
    }

    @Test
    @Proves("T-AUTH-006")
    void everyAccountStateGetsOneExactAnswerPerEndpoint() throws Exception {
        Map<String, String> login = new LinkedHashMap<>();
        Map<String, String> reset = new LinkedHashMap<>();
        Map<String, String> registration = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> state : states().entrySet()) {
            String username = state.getValue()[0];
            login.put(state.getKey(), answer(SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), username,
                    state.getValue()[1]).andReturn()));
            reset.put(state.getKey(), answer(new PasswordResets(mockMvc).request(email(username)).andReturn()));
            registration.put(state.getKey(), answer(new Registrations(mockMvc)
                    .register(Registrations.freshUsername(), email(username)).andReturn()));
        }

        assertThat(login).allSatisfy((state, answer) -> assertThat(answer).as(state).isEqualTo("401 " + LOGIN_REFUSAL));
        assertThat(reset).allSatisfy((state, answer) -> assertThat(answer).as(state).isEqualTo("202 "));
        assertThat(registration).allSatisfy((state, answer) -> assertThat(answer).as(state).isEqualTo("202 "));
    }
}
