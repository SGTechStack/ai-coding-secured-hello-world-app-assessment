package sg.securedhello.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.config.OriginsProperties;
import sg.securedhello.credential.CredentialTokenHash;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.PasswordResets;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;

/**
 * {@code POST /api/password-reset/request} through the full context (PRD Story 6): one answer for every address, a
 * link only for an activated account, on the configured origin, and an audit row that names no one.
 */
class PasswordResetRequestTest extends CtxDefaultTest {

    private static final String REQUESTED_ROW = "Password reset requested.";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OriginsProperties origins;

    @Autowired
    private AuditEmitter emitter;

    private Accounts accounts;
    private PasswordResets resets;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        resets = new PasswordResets(mockMvc);
    }

    /** One request's wire answer. */
    private record Answer(int status, String body) {
    }

    private Answer answer(String email) throws Exception {
        MvcResult result = resets.request(email).andReturn();
        return new Answer(result.getResponse().getStatus(),
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Account locked() {
        Account account = accounts.user();
        jdbc.update("UPDATE users SET failed_login_attempts = 5, locked_until = ? WHERE id = ?",
                Timestamp.from(clock.instant().plus(Duration.ofMinutes(20))), account.id());
        return account;
    }

    private int pendingResetTokens(Account account) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM credential_tokens WHERE user_id = ? AND type = 'PASSWORD_RESET'"
                + " AND used_at IS NULL", Integer.class, account.id());
    }

    @Test
    void everyAddressGetsTheIdenticalAnswerAndOnlyAnActivatedAccountIsSentALink() throws Exception {
        Map<String, Account> states = new LinkedHashMap<>();
        states.put("activated", accounts.user());
        states.put("locked", locked());
        states.put("not yet activated", accounts.notActivated());
        states.put("disabled", accounts.disabled());
        Answer unknown = answer(Accounts.unknownUsername() + "@example.test");

        states.forEach((state, account) -> {
            Answer answer;
            try {
                answer = answer(PasswordResets.emailOf(account));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            assertThat(answer).as(state).isEqualTo(unknown);
        });

        assertThat(unknown.status()).isEqualTo(202);
        assertThat(unknown.body()).isEmpty();
        assertThat(emails.to(PasswordResets.emailOf(states.get("activated")))).singleElement();
        assertThat(emails.to(PasswordResets.emailOf(states.get("locked")))).as("a locked account may reset (ADR-009)")
                .singleElement();
        assertThat(emails.to(PasswordResets.emailOf(states.get("not yet activated")))).as("R-CRED-012").isEmpty();
        assertThat(pendingResetTokens(states.get("not yet activated"))).isZero();
        assertThat(emails.to(PasswordResets.emailOf(states.get("disabled")))).isEmpty();
        assertThat(pendingResetTokens(states.get("disabled"))).isZero();
    }

    @Test
    void theLinkIsOnTheConfiguredOriginAndItsTokenInTheFragment() throws Exception {
        Account account = accounts.user();

        resets.request(PasswordResets.emailOf(account)).andExpect(status().isAccepted());

        assertThat(emails.to(PasswordResets.emailOf(account))).singleElement().satisfies(email -> {
            assertThat(email.type()).isEqualTo(CredentialTokenType.PASSWORD_RESET);
            assertThat(email.link()).hasScheme(URI.create(origins.spa()).getScheme())
                    .hasAuthority(URI.create(origins.spa()).getAuthority()).hasPath("/reset");
            assertThat(email.link().getRawFragment()).startsWith("token=");
        });
    }

    @Test
    @Proves("T-CRED-012")
    void theResetTokenIsStoredOnlyAsItsDomainSeparatedHashWithAThirtyMinuteLifetime() throws Exception {
        Account account = accounts.user();
        resets.request(PasswordResets.emailOf(account)).andExpect(status().isAccepted());
        String token = emails.latestToken(PasswordResets.emailOf(account), CredentialTokenType.PASSWORD_RESET)
                .orElseThrow();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        var row = jdbc.queryForMap("SELECT token_hash, created_at, expires_at, used_at FROM credential_tokens"
                + " WHERE user_id = ? AND type = 'PASSWORD_RESET'", account.id());
        assertThat(row).containsEntry("TOKEN_HASH", CredentialTokenHash.hash(CredentialTokenType.PASSWORD_RESET, token))
                .containsEntry("USED_AT", null);
        assertThat(Duration.between(((OffsetDateTime) row.get("CREATED_AT")).toInstant(),
                ((OffsetDateTime) row.get("EXPIRES_AT")).toInstant())).isEqualTo(Duration.ofMinutes(30));
        assertThat(columnsHolding(token)).as("no column of any table holds the plaintext").isEmpty();
    }

    @Test
    void anAddressIsMatchedInItsCanonicalForm() throws Exception {
        Account account = accounts.user();

        resets.request("  " + PasswordResets.emailOf(account).toUpperCase(java.util.Locale.ROOT) + " ")
                .andExpect(status().isAccepted());

        assertThat(emails.to(PasswordResets.emailOf(account))).singleElement();
    }

    @Test
    void aValueThatCannotBeAnAddressIsAValidationFailure() throws Exception {
        resets.request("not an address").andExpect(problem(ErrorCode.VALIDATION_FAILED));
        new Registrations(mockMvc).send("/api/password-reset/request", "{}")
                .andExpect(problem(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void aNewRequestCancelsTheEarlierPendingToken() throws Exception {
        Account account = accounts.user();
        resets.request(PasswordResets.emailOf(account)).andExpect(status().isAccepted());
        String first = emails.latestToken(PasswordResets.emailOf(account), CredentialTokenType.PASSWORD_RESET)
                .orElseThrow();

        resets.request(PasswordResets.emailOf(account)).andExpect(status().isAccepted());

        assertThat(pendingResetTokens(account)).isOne();
        resets.confirm(first, PasswordResets.NEW_PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
    }

    @Test
    void issuingATokenClearsNeitherTheLockNorTheCap() throws Exception {
        Account account = locked();
        jdbc.update("UPDATE users SET password_disabled_at = ? WHERE id = ?", Timestamp.from(clock.instant()),
                account.id());
        var before = accounts.lockoutState(account);

        resets.request(PasswordResets.emailOf(account)).andExpect(status().isAccepted());

        assertThat(accounts.lockoutState(account)).isEqualTo(before);
    }

    @Test
    void theRequestedRowNamesNoAccountWhetherOrNotTheAddressIsRegistered() throws Exception {
        emitter.closeKeyingWindow();
        try (AuditCapture audit = AuditCapture.start()) {
            new PasswordResets(mockMvc, "198.51.100.61").request(PasswordResets.emailOf(accounts.user()))
                    .andExpect(status().isAccepted());
            new PasswordResets(mockMvc, "198.51.100.62").request(Accounts.unknownUsername() + "@example.test")
                    .andExpect(status().isAccepted());
            emitter.closeKeyingWindow();

            assertThat(audit.withMessage(REQUESTED_ROW)).hasSize(2).allSatisfy(row -> assertThat(row)
                    .containsEntry("event.action", "password-reset").containsEntry("event.count", 1)
                    .doesNotContainKey("user.id"));
        }
    }

    @Test
    @Proves("T-CRED-018")
    void aLinkOrOriginFieldInTheBodyIsIgnoredOnResetAndRegistration() throws Exception {
        Account account = accounts.user();
        String username = Registrations.freshUsername();
        String registered = Registrations.emailFor(username);
        Registrations sender = new Registrations(mockMvc);

        sender.send("/api/password-reset/request", """
                {"email":"%s","link":"https://attacker.example/reset","origin":"https://attacker.example"}"""
                .formatted(PasswordResets.emailOf(account))).andExpect(status().isAccepted());
        sender.send("/api/register", """
                {"username":"%s","email":"%s","resetUrl":"https://attacker.example/activate",\
                "origin":"https://attacker.example"}""".formatted(username, registered))
                .andExpect(status().isAccepted());

        assertThat(emails.to(PasswordResets.emailOf(account))).singleElement()
                .satisfies(email -> assertThat(email.link().toString()).startsWith(origins.spa() + "/reset#token="));
        assertThat(emails.to(registered)).singleElement()
                .satisfies(email -> assertThat(email.link().toString()).startsWith(origins.spa() + "/activate#token="));
    }

    /** Every {@code table.column} whose value, as text or as bytes, contains {@code secret}. */
    private List<String> columnsHolding(String secret) {
        List<String> found = new ArrayList<>();
        for (String table : jdbc.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES"
                + " WHERE TABLE_SCHEMA = 'PUBLIC'", String.class)) {
            jdbc.query("SELECT * FROM \"" + table + "\"", (ResultSet rs) -> {
                for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
                    Object value = rs.getObject(column);
                    String text = value instanceof byte[] bytes ? new String(bytes, StandardCharsets.ISO_8859_1)
                            : String.valueOf(value);
                    if (text.contains(secret)) {
                        found.add(table + "." + rs.getMetaData().getColumnName(column));
                    }
                }
            });
        }
        return found;
    }
}
