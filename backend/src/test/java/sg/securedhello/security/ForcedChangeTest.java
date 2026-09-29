package sg.securedhello.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TestSecrets;
import sg.securedhello.user.UserAccount;

import tools.jackson.databind.json.JsonMapper;

/**
 * A forced-change session reaches the five allowlisted routes only, and completing the change frees it (ADR-046;
 * ADR-047; REJ-055). The last test walks the bootstrap administrator this context seeded from {@link TestSecrets}.
 */
class ForcedChangeTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NEW_PASSWORD = "copper lantern drifts over the quay";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
    }

    private Account forcedChange(String role) {
        Account account = accounts.withRole(role);
        jdbc.update("UPDATE users SET force_password_change = TRUE, credential_issued_at = ? WHERE id = ?",
                Timestamp.from(clock.instant()), account.id());
        return account;
    }

    private ResultActions change(CsrfSession session, String current, String next) throws Exception {
        return mockMvc.perform(patch("/api/profile/password").with(session.inHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("currentPassword", current, "newPassword", next))));
    }

    private Map<String, Object> flags(String username) {
        return jdbc.queryForMap("SELECT force_password_change, credential_issued_at FROM users WHERE username = ?",
                username);
    }

    /** Every route off the allowlist, signed-in or public, known or not, with a valid CSRF token on unsafe methods. */
    @ParameterizedTest
    @CsvSource({"USER, GET, /api/hello", "ADMIN, GET, /api/hello", "ADMIN, GET, /api/mfa/totp",
            "ADMIN, POST, /api/mfa/totp/confirm", "ADMIN, GET, /api/admin/users", "USER, GET, /api/admin/users",
            "ADMIN, GET, /api/admin/roles/USER", "USER, POST, /api/register", "USER, POST, /api/password-reset/request",
            "USER, GET, /api/no-such-route", "USER, DELETE, /api/profile"})
    void everyRouteOffTheAllowlistIsRefusedWithPasswordChangeRequired(String role, String method, String path)
            throws Exception {
        CsrfSession session = SignedIn.as(mockMvc, forcedChange(role));

        mockMvc.perform(request(HttpMethod.valueOf(method), path).with(session.inHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(problem(ErrorCode.PASSWORD_CHANGE_REQUIRED));
    }

    @Test
    void theFiveAllowlistedRoutesStayReachable() throws Exception {
        Account account = forcedChange("USER");

        MvcResult login = SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), account.username(),
                        account.password())
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordChangeRequired").value(true)).andReturn();
        // GET /api/csrf, on the signed-in session.
        CsrfSession session = SignedIn.refreshed(mockMvc, login.getResponse().getCookie("SESSION"));
        mockMvc.perform(get("/api/profile").cookie(session.cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
        // The change endpoint answers for itself: a wrong current password is its own 400, not the filter's 403.
        change(session, "not-the-current-password", NEW_PASSWORD).andExpect(problem(ErrorCode.VALIDATION_FAILED));
        mockMvc.perform(get("/api/hello").cookie(session.cookie()))
                .andExpect(problem(ErrorCode.PASSWORD_CHANGE_REQUIRED));
        assertThat(flags(account.username())).containsEntry("FORCE_PASSWORD_CHANGE", true);
        mockMvc.perform(post("/api/logout").with(session.inHeader())).andExpect(status().isNoContent());
    }

    @Test
    void completingTheChangeClearsBothFlagsAndFreesTheSession() throws Exception {
        Account account = forcedChange("USER");
        CsrfSession session = SignedIn.as(mockMvc, account);

        MvcResult result = change(session, account.password(), NEW_PASSWORD).andExpect(status().isNoContent())
                .andReturn();

        assertThat(flags(account.username())).containsEntry("FORCE_PASSWORD_CHANGE", false)
                .containsEntry("CREDENTIAL_ISSUED_AT", null);
        Cookie rotated = result.getResponse().getCookie("SESSION");
        mockMvc.perform(get("/api/profile").cookie(rotated)).andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(false));
        mockMvc.perform(get("/api/hello").cookie(rotated)).andExpect(status().isOk());
    }

    /**
     * The seeded administrator: its forced-change credential works for 30 days on the {@code Clock}, confines
     * the session to the allowlist, and completing the change clears both flags (ADR-046; ADR-047).
     */
    @Test
    void theSeededAdministratorIsAForcedChangeCredentialWithAThirtyDayLife() throws Exception {
        String admin = TestSecrets.ADMIN_USERNAME;
        assertThat(flags(admin)).containsEntry("FORCE_PASSWORD_CHANGE", true);
        assertThat(flags(admin).get("CREDENTIAL_ISSUED_AT")).isNotNull();
        jdbc.update("UPDATE users SET credential_issued_at = ? WHERE username = ?", Timestamp.from(clock.instant()),
                admin);

        // Just under 30 days: the column keeps microseconds, the clock finer, so the exact instant is proven in
        // ForcedChangeExpiryCheckTest instead.
        clock.advance(UserAccount.FORCED_CHANGE_GRACE.minusMillis(1));
        CsrfSession session = SignedIn.as(mockMvc, new Account(null, admin, TestSecrets.ADMIN_PASSWORD));
        mockMvc.perform(get("/api/profile").cookie(session.cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
        mockMvc.perform(get("/api/hello").cookie(session.cookie()))
                .andExpect(problem(ErrorCode.PASSWORD_CHANGE_REQUIRED));

        clock.advance(Duration.ofSeconds(1));
        SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), admin, TestSecrets.ADMIN_PASSWORD)
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));

        jdbc.update("UPDATE users SET credential_issued_at = ? WHERE username = ?", Timestamp.from(clock.instant()),
                admin);
        CsrfSession renewed = SignedIn.as(mockMvc, new Account(null, admin, TestSecrets.ADMIN_PASSWORD));
        Cookie rotated = change(renewed, TestSecrets.ADMIN_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(flags(admin)).containsEntry("FORCE_PASSWORD_CHANGE", false)
                .containsEntry("CREDENTIAL_ISSUED_AT", null);
        // Freed, the administrator reaches its own routes: the self-read, which both roles share; /api/hello is USER's.
        mockMvc.perform(get("/api/profile").cookie(rotated)).andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(false));
        mockMvc.perform(get("/api/hello").cookie(rotated)).andExpect(problem(ErrorCode.ACCESS_DENIED));
    }
}
