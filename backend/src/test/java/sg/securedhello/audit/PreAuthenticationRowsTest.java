package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.credential.CredentialTokens;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.PasswordResets;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.SignedIn;

/**
 * The anonymous rows: a failed login for no account (row 2), an invalid session (row 11), a registration (rows 16 and
 * 17) and a failed token redemption (row 20). None names an account it has not resolved, whatever the MDC holds
 * (Std §3.4; REJ-042); only a new pending registration's row 16 names the account it created (ADR-032).
 */
class PreAuthenticationRowsTest extends CtxDefaultTest {

    /** What a careless filter or library might leave in the MDC; the formatter must never copy it onto a row. */
    private static final String PLANTED = UUID.randomUUID().toString();

    @Autowired
    private AuditEmitter emitter;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CredentialTokens tokens;

    private Accounts accounts;
    private Registrations registrations;
    private PasswordResets resets;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        registrations = new Registrations(mockMvc);
        resets = new PasswordResets(mockMvc);
        emitter.closeKeyingWindow();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private static void plantAUserIdInTheMdc() {
        MDC.put("user.id", PLANTED);
        MDC.put("userId", PLANTED);
        MDC.put("user_id", PLANTED);
    }

    @Test
    @Proves("T-AUD-026")
    void noAnonymousRowCarriesAUserIdEvenWithOneInTheMdc() throws Exception {
        Account activated = accounts.user();
        String staleSession = Base64.getEncoder().encodeToString(UUID.randomUUID().toString()
                .getBytes(StandardCharsets.UTF_8));
        plantAUserIdInTheMdc();

        List<Map<String, Object>> rows;
        try (AuditCapture audit = AuditCapture.start()) {
            SignedIn.loginFrom(mockMvc, "127.0.0.1", Accounts.unknownUsername(), Accounts.WRONG_PASSWORD)
                    .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
            mockMvc.perform(get("/actuator/health").cookie(new Cookie("SESSION", staleSession)))
                    .andExpect(status().isOk());
            registrations.register(Registrations.freshUsername(), PasswordResets.emailOf(activated))
                    .andExpect(status().isAccepted());
            registrations.register(activated.username(), Registrations.emailFor(Registrations.freshUsername()))
                    .andExpect(problem(ErrorCode.VALIDATION_FAILED));
            registrations.activate("B".repeat(43), Registrations.PASSWORD)
                    .andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
            emitter.closeKeyingWindow();
            rows = audit.rows();
        }

        assertThat(rows).extracting(row -> row.get("message") + " " + row.get("event.reason")).containsExactly(
                "Login failed. UNKNOWN_USER",
                "Registration accepted. EXISTING_ADDRESS",
                "Registration refused. USERNAME_UNAVAILABLE",
                "Credential token redemption failed. TOKEN_UNKNOWN",
                "Invalid session presented. UNKNOWN_OR_EXPIRED");
        assertThat(rows).allSatisfy(row -> assertThat(row).doesNotContainKeys("user.id", "userId", "user_id",
                "user.target.id")).noneMatch(row -> row.containsValue(PLANTED));
    }

    @Test
    void aNewPendingRegistrationIsNamedByItsAccount() throws Exception {
        String username = Registrations.freshUsername();
        try (AuditCapture audit = AuditCapture.start()) {
            registrations.register(username, Registrations.emailFor(username)).andExpect(status().isAccepted());

            UUID created = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username);
            assertThat(audit.rows()).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("message", "Registration accepted.")
                    .containsEntry("event.action", "user-provisioning")
                    .containsEntry("event.type", List.of("creation"))
                    .containsEntry("event.reason", "NEW_ACCOUNT")
                    .containsEntry("user.id", created.toString()));
        }
        try (AuditCapture audit = AuditCapture.start()) {
            registrations.register(username, Registrations.emailFor(username)).andExpect(status().isAccepted());

            assertThat(audit.rows()).as("a renewed pending registration is an existing address").singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("event.reason", "EXISTING_ADDRESS")
                            .doesNotContainKey("user.id"));
        }
    }

    @Test
    void aRefusedRegistrationIsAWarningThatNamesNoAccount() throws Exception {
        Account taken = accounts.user();
        try (AuditCapture audit = AuditCapture.start()) {
            registrations.register(taken.username(), Registrations.emailFor(Registrations.freshUsername()))
                    .andExpect(problem(ErrorCode.VALIDATION_FAILED));

            assertThat(audit.rows()).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("message", "Registration refused.")
                    .containsEntry("log.level", "WARN")
                    .containsEntry("event.severity", "low")
                    .containsEntry("event.outcome", "failure")
                    .doesNotContainKey("user.id"));
        }
    }

    @Test
    void aFailedRedemptionNamesWhyOnItsRowOnly() throws Exception {
        Account account = accounts.user();
        String consumed = tokens.mint(account.id(), CredentialTokenType.PASSWORD_RESET);
        resets.confirm(consumed, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());
        String expired = tokens.mint(accounts.user().id(), CredentialTokenType.PASSWORD_RESET);
        clock.advance(CredentialTokenType.PASSWORD_RESET.lifetime().plus(Duration.ofSeconds(1)));
        String activationToken = tokens.mint(accounts.notActivated().id(), CredentialTokenType.ACTIVATION);
        String liveForActivated = tokens.mint(accounts.user().id(), CredentialTokenType.ACTIVATION);

        try (AuditCapture audit = AuditCapture.start()) {
            resets.confirm(consumed, "another passphrase entirely").andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
            resets.confirm(expired, PasswordResets.NEW_PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
            resets.confirm(activationToken, PasswordResets.NEW_PASSWORD)
                    .andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));

            registrations.activate(liveForActivated, Registrations.PASSWORD)
                    .andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));

            assertThat(audit.rows()).allSatisfy(row -> assertThat(row)
                    .containsEntry("message", "Credential token redemption failed.")
                    .containsEntry("event.action", "password-reset")
                    .containsEntry("log.level", "WARN")
                    .doesNotContainKey("user.id"))
                    .extracting(row -> row.get("event.reason"))
                    .as("a live activation token for an account that is no longer pending is not called expired")
                    .containsExactly("TOKEN_CONSUMED", "TOKEN_EXPIRED", "TOKEN_UNKNOWN", "TOKEN_UNKNOWN");
        }
    }
}
