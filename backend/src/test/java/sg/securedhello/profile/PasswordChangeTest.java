package sg.securedhello.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.password.PasswordRule;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;

import tools.jackson.databind.json.JsonMapper;

/** {@code PATCH /api/profile/password} over the full context: the policy, history, tokens and sessions (ADR-008). */
class PasswordChangeTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Passphrases the policy accepts, none a context term of a fixture account. */
    private static final String FIRST = "velvet harbour quietly hums";
    private static final String SECOND = "lantern orchard copper tide";
    private static final String THIRD = "sunflower meadow drifts slowly";
    private static final String FOURTH = "my neighbour keeps unusual bees";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
    }

    private ResultActions change(CsrfSession session, String current, String next) throws Exception {
        return mockMvc.perform(patch("/api/profile/password").with(session.inHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("currentPassword", current, "newPassword", next))));
    }

    /** Changes the password and returns the rotated session, with its new token. */
    private CsrfSession changed(CsrfSession session, String current, String next) throws Exception {
        MvcResult result = change(session, current, next).andExpect(status().isNoContent()).andReturn();
        Cookie rotated = result.getResponse().getCookie("SESSION");
        assertThat(rotated).as("the rotated session cookie").isNotNull();
        // Each change is a later instant, so the history's order is the order of the changes.
        clock.advance(Duration.ofSeconds(1));
        return SignedIn.refreshed(mockMvc, rotated);
    }

    private String hash(Account account) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, account.id());
    }

    private List<String> history(Account account) {
        return jdbc.queryForList("SELECT password_hash FROM password_history WHERE user_id = ? ORDER BY created_at",
                String.class, account.id());
    }

    private int loginStatus(Account account, String password) throws Exception {
        return SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), account.username(), password).andReturn()
                .getResponse().getStatus();
    }

    @Test
    void aValidChangeAnswers204AndOnlyTheNewPasswordSignsIn() throws Exception {
        Account account = accounts.user();
        changed(SignedIn.as(mockMvc, account), account.password(), FIRST);

        assertThat(hash(account)).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(FIRST, hash(account))).isTrue();
        assertThat(loginStatus(account, FIRST)).isEqualTo(200);
        assertThat(loginStatus(account, account.password())).isEqualTo(401);
    }

    static Stream<Arguments> rejections() {
        return Stream.of(
                Arguments.of("fourteen chars", PasswordRule.MIN_LENGTH),
                // 30 characters, 90 UTF-8 bytes: refused by our validator, never a 500 from the encoder.
                Arguments.of("日".repeat(30), PasswordRule.MAX_BYTES),
                Arguments.of("QWERTYUIOPASDFGH", PasswordRule.BLOCKLISTED),
                Arguments.of("Secured Hello World 2026!", PasswordRule.CONTEXT_TERM),
                Arguments.of("passwordpassword1234", PasswordRule.TOO_WEAK),
                Arguments.of(Accounts.PASSWORD, PasswordRule.HISTORY_REUSE));
    }

    @ParameterizedTest
    @MethodSource("rejections")
    @Proves("T-CRED-001")
    void eachRuleRejectsWithItsOwnRuleAndChangesNothing(String candidate, PasswordRule rule) throws Exception {
        Account account = accounts.user();
        String before = hash(account);

        change(SignedIn.as(mockMvc, account), account.password(), candidate)
                .andExpect(problem(ErrorCode.PASSWORD_REJECTED))
                .andExpect(jsonPath("$.rule").value(rule.name()));

        assertThat(hash(account)).isEqualTo(before);
        assertThat(history(account)).isEmpty();
    }

    @Test
    @Proves("T-CRED-008")
    void aWrongCurrentPasswordIsRefusedAndChangesNothing() throws Exception {
        Account account = accounts.user();
        CsrfSession session = SignedIn.as(mockMvc, account);
        String before = hash(account);
        Integer failures = jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class,
                account.id());
        String sessionId = SessionRows.idOf(session.cookie().getValue());

        change(session, "not the current password", FIRST).andExpect(problem(ErrorCode.VALIDATION_FAILED));
        // Past 72 bytes too: refused as a mismatch, never a 500 from the encoder.
        change(session, "x".repeat(100), FIRST).andExpect(problem(ErrorCode.VALIDATION_FAILED));

        assertThat(hash(account)).isEqualTo(before);
        assertThat(history(account)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class,
                account.id())).isEqualTo(failures);
        assertThat(new SessionRows(jdbc).exists(sessionId)).as("the session is not rotated or ended").isTrue();
        mockMvc.perform(get("/api/hello").cookie(session.cookie())).andExpect(status().isOk());
    }

    @Test
    @Proves("T-CRED-002")
    void theCurrentPasswordAndTheTwoBeforeItAreRefusedAndTheOneBeforeThoseIsAccepted() throws Exception {
        Account account = accounts.user();
        CsrfSession session = SignedIn.as(mockMvc, account);
        session = changed(session, account.password(), FIRST);
        session = changed(session, FIRST, SECOND);
        session = changed(session, SECOND, THIRD);

        for (String reused : List.of(THIRD, SECOND, FIRST)) {
            change(session, THIRD, reused).andExpect(problem(ErrorCode.PASSWORD_REJECTED))
                    .andExpect(jsonPath("$.rule").value(PasswordRule.HISTORY_REUSE.name()));
        }
        changed(session, THIRD, account.password());
    }

    @Test
    @Proves("T-CRED-021")
    void theHistoryIsTrimmedToThreeEvictingTheOldest() throws Exception {
        Account account = accounts.user();
        CsrfSession session = SignedIn.as(mockMvc, account);
        session = changed(session, account.password(), FIRST);
        session = changed(session, FIRST, SECOND);
        session = changed(session, SECOND, THIRD);
        changed(session, THIRD, FOURTH);

        List<String> retained = history(account);
        assertThat(retained).hasSize(3);
        assertThat(retained).noneMatch(hash -> passwordEncoder.matches(FIRST, hash));
        assertThat(passwordEncoder.matches(FOURTH, retained.get(2))).as("newest is the current").isTrue();
        assertThat(retained.get(2)).isEqualTo(hash(account));
    }

    @Test
    void aChangeInvalidatesPendingResetTokensOnlyAndCompletesAForcedChange() throws Exception {
        Account account = accounts.user();
        jdbc.update("UPDATE users SET force_password_change = TRUE, credential_issued_at = ? WHERE id = ?",
                Timestamp.from(clock.instant()), account.id());
        UUID pendingReset = token(account, "PASSWORD_RESET", false);
        UUID usedReset = token(account, "PASSWORD_RESET", true);
        UUID pendingActivation = token(account, "ACTIVATION", false);

        changed(SignedIn.as(mockMvc, account), account.password(), FIRST);

        assertThat(jdbc.queryForList("SELECT id FROM credential_tokens WHERE user_id = ?", UUID.class, account.id()))
                .containsExactlyInAnyOrder(usedReset, pendingActivation).doesNotContain(pendingReset);
        Map<String, Object> user = jdbc.queryForMap(
                "SELECT force_password_change, credential_issued_at FROM users WHERE id = ?", account.id());
        assertThat(user.get("FORCE_PASSWORD_CHANGE")).isEqualTo(false);
        assertThat(user.get("CREDENTIAL_ISSUED_AT")).isNull();
    }

    private UUID token(Account account, String type, boolean used) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO credential_tokens (id, user_id, type, token_hash, expires_at, used_at, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)", id, account.id(), type,
                UUID.randomUUID().toString().replace("-", "") + "00000000000000000000000000000000",
                Timestamp.from(clock.instant().plus(Duration.ofMinutes(30))), used ? now : null, now);
        return id;
    }

    @Test
    @Proves("T-CRED-006")
    void aPasswordSetInOneNormalFormSignsInWithTheOther() throws Exception {
        Account decomposed = accounts.user();
        changed(SignedIn.as(mockMvc, decomposed), decomposed.password(), "café au lait on the balcony");
        assertThat(loginStatus(decomposed, "café au lait on the balcony")).isEqualTo(200);

        Account composed = accounts.user();
        changed(SignedIn.as(mockMvc, composed), composed.password(), "naïve orchard lantern tide");
        assertThat(loginStatus(composed, "naïve orchard lantern tide")).isEqualTo(200);
    }

    @Test
    void aBodyWithoutBothPasswordsIsValidationFailed() throws Exception {
        Account account = accounts.user();
        mockMvc.perform(patch("/api/profile/password").with(SignedIn.as(mockMvc, account).inHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"" + FIRST + "\"}"))
                .andExpect(problem(ErrorCode.VALIDATION_FAILED));
        assertThat(history(account)).isEmpty();
    }

    @Test
    void anAnonymousCallerIsRefused() throws Exception {
        mockMvc.perform(patch("/api/profile/password").with(CsrfSession.validToken(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("currentPassword", FIRST, "newPassword", SECOND))))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }
}
