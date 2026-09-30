package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.credential.CredentialTokenHash;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AdminCredentialCalls;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;
import sg.securedhello.user.Tombstones;

/**
 * {@code POST /api/admin/users}, admin create by invite (ADR-006; R-ADM-012): a pending registration whose activation
 * token is returned once, specific about taken identifiers, and protected from self-registration by its token's
 * admin-issued marker, never by its role (ADR-007 amendment).
 */
class AdminInviteTest extends CtxDefaultTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private Tombstones tombstones;

    private Accounts accounts;
    private AdminCredentialCalls admin;
    private Account actor;
    private Registrations registrations;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new Accounts(jdbc, passwordEncoder);
        actor = accounts.withRole("ADMIN");
        admin = AdminCredentialCalls.signedIn(mockMvc, new TotpFactors(jdbc, cipher, clock), actor);
        registrations = new Registrations(mockMvc);
    }

    private int usersNamed(String username) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
    }

    private int usersWithEmail(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
    }

    private void tombstone(String username, String canonicalEmail) {
        jdbc.update("INSERT INTO deleted_users (user_id, username, email_hmac, deleted_at, deleted_by_id)"
                        + " VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), username, tombstones.emailHmac(canonicalEmail),
                Timestamp.from(clock.instant()), actor.id());
    }

    private Map<String, Object> account(String username) {
        return jdbc.queryForMap("SELECT id, role, enabled, activated_at, password_hash, force_password_change,"
                + " credential_issued_at FROM users WHERE username = ?", username);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN"})
    void aFreshInviteCreatesAPendingAccountAndReturnsItsActivationTokenOnceUncached(String role) throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);

        String token;
        try (AuditCapture audit = AuditCapture.start()) {
            ResultActions issued = admin.invite(username, email, role).andExpect(status().isCreated())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.token").isString());
            token = AdminCredentialCalls.token(issued);
            UUID id = AdminCredentialCalls.userId(issued);
            assertThat(audit.withMessage("Account invited.")).singleElement().satisfies(row ->
                    assertThat(row).containsEntry("event.action", "user-provisioning")
                            .containsEntry("user.id", actor.id().toString())
                            .containsEntry("user.target.id", id.toString()));
            assertThat(audit.rows()).allSatisfy(row -> assertThat(row.values()).doesNotContain(token));
        }

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        // No password is taken or generated, and no forced change is set (ADR-006; R-ADM-012).
        Map<String, Object> row = account(username);
        assertThat(row).containsEntry("ROLE", role).containsEntry("ENABLED", true)
                .containsEntry("ACTIVATED_AT", null).containsEntry("PASSWORD_HASH", null)
                .containsEntry("FORCE_PASSWORD_CHANGE", false).containsEntry("CREDENTIAL_ISSUED_AT", null);
        assertThat(jdbc.queryForMap("SELECT type, token_hash, admin_issued FROM credential_tokens WHERE user_id = ?",
                row.get("ID"))).containsEntry("TYPE", "ACTIVATION").containsEntry("ADMIN_ISSUED", true)
                .containsEntry("TOKEN_HASH", CredentialTokenHash.hash(CredentialTokenType.ACTIVATION, token));
        // Nothing is emailed: the administrator hands the token on (ADR-006).
        assertThat(emails.to(email)).isEmpty();
    }

    @Test
    void anInvitesTokenActivatesTheAccountThroughTheOrdinaryActivation() throws Exception {
        String username = Registrations.freshUsername();
        String token = AdminCredentialCalls.token(admin.invite(username, Registrations.emailFor(username), "USER")
                .andExpect(status().isCreated()));

        registrations.activate(token, Registrations.PASSWORD).andExpect(status().isNoContent());

        assertThat(account(username).get("ACTIVATED_AT")).isNotNull();
        CsrfSession session = SignedIn.as(mockMvc, new Account(null, username, Registrations.PASSWORD));
        mockMvc.perform(get("/api/hello").cookie(session.cookie())).andExpect(status().isOk());
        registrations.activate(token, Registrations.PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
    }

    @Test
    @Proves("T-ADM-001")
    void aUsernameOrEmailALiveAccountHoldsIsRefusedWithUserExistsAndNothingIsCreated() throws Exception {
        Account holder = accounts.user();
        Account pending = accounts.notActivated();
        String fresh = Registrations.freshUsername();

        admin.invite(holder.username(), Registrations.emailFor(fresh), "USER")
                .andExpect(problem(ErrorCode.USER_EXISTS));
        admin.invite(fresh, holder.username() + "@example.test", "USER").andExpect(problem(ErrorCode.USER_EXISTS));
        admin.invite(fresh, pending.username() + "@EXAMPLE.test", "ADMIN")
                .andExpect(problem(ErrorCode.USER_EXISTS));

        assertThat(usersNamed(fresh)).isZero();
        assertThat(usersWithEmail(Registrations.emailFor(fresh))).isZero();
        assertThat(usersNamed(holder.username())).isOne();
    }

    @Test
    @Proves("T-ADM-002")
    void aTombstonedUsernameOrEmailIsRefusedByAdminCreateAndBySelfRegistration() throws Exception {
        String deletedName = Registrations.freshUsername();
        String deletedEmail = Registrations.emailFor(deletedName);
        tombstone(deletedName, deletedEmail);
        String fresh = Registrations.freshUsername();

        // Admin create: specific, USER_EXISTS, for either identifier.
        admin.invite(deletedName, Registrations.emailFor(fresh), "USER").andExpect(problem(ErrorCode.USER_EXISTS));
        admin.invite(fresh, deletedEmail, "USER").andExpect(problem(ErrorCode.USER_EXISTS));
        // Self-registration: the uniform 202 on the email axis, the username refused, and nothing created either way.
        registrations.register(fresh, deletedEmail).andExpect(status().isAccepted());
        registrations.register(deletedName, Registrations.emailFor(Registrations.freshUsername()))
                .andExpect(problem(ErrorCode.VALIDATION_FAILED))
                .andExpect(jsonPath("$.rule").value("USERNAME_UNAVAILABLE"));

        assertThat(usersNamed(deletedName)).isZero();
        assertThat(usersNamed(fresh)).isZero();
        assertThat(usersWithEmail(deletedEmail)).isZero();
        assertThat(emails.to(deletedEmail)).isEmpty();
    }

    /**
     * Over-length, malformed and out-of-pattern identifiers, each with the other identifier valid. T-CRED-003's rows ask
     * for an {@code errors[]} member the error contract does not declare (ADR-031), so it stays on the ledger until
     * that conflict is decided; this asserts what the contract allows.
     */
    static Stream<Arguments> invalidIdentifiers() {
        String longLocal = "a".repeat(64);
        String overLengthEmail = longLocal + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(60)
                + ".test";
        return Stream.of(
                Arguments.of("over-length username", "a".repeat(33), null),
                Arguments.of("over-length email", null, overLengthEmail),
                Arguments.of("malformed email", null, "not-an-address"),
                Arguments.of("username outside the pattern", "semi;colon", null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidIdentifiers")
    void registrationAndAdminCreateRefuseAnInvalidIdentifierWithValidationFailedAndCreateNothing(String label,
            String badUsername, String badEmail) throws Exception {
        String username = badUsername != null ? badUsername : Registrations.freshUsername();
        String email = badEmail != null ? badEmail : Registrations.emailFor(Registrations.freshUsername());

        registrations.register(username, email).andExpect(problem(ErrorCode.VALIDATION_FAILED))
                .andExpect(jsonPath("$.rule").doesNotExist());
        admin.invite(username, email, "USER").andExpect(problem(ErrorCode.VALIDATION_FAILED))
                .andExpect(jsonPath("$.rule").doesNotExist());

        assertThat(usersNamed(username)).isZero();
        assertThat(usersWithEmail(email.toLowerCase(java.util.Locale.ROOT))).isZero();
    }

    @Test
    void aRoleOutsideTheTwoOrAMissingMemberIsInvalid() throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);

        admin.invite(username, email, "SUPERUSER").andExpect(problem(ErrorCode.VALIDATION_FAILED));
        admin.send("/api/admin/users", "{\"username\":\"" + username + "\",\"email\":\"" + email + "\"}")
                .andExpect(problem(ErrorCode.VALIDATION_FAILED));
        admin.send("/api/admin/users", "{\"username\":\"" + username + "\",\"email\":\"" + email
                + "\",\"role\":\"USER\",\"password\":\"chosen by the admin\"}").andExpect(status().isCreated());

        assertThat(account(username)).containsEntry("PASSWORD_HASH", null);
    }

    /**
     * Review 14–16 M1, and the ADR-007 amendment: a stranger who knows an invitee's address registers it again, under a
     * username of their choosing. The invite is marked by its admin-issued token, whatever its role, so the stranger
     * gets the uniform 202 and changes nothing: no rename, no cancelled token, no email.
     */
    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN"})
    void aSelfRegistrationOfAnInviteesAddressLeavesTheInviteAndItsTokenIntact(String role) throws Exception {
        String invited = Registrations.freshUsername();
        String email = Registrations.emailFor(invited);
        String token = AdminCredentialCalls.token(admin.invite(invited, email, role).andExpect(status().isCreated()));
        String strangers = Registrations.freshUsername();

        registrations.register(strangers, email).andExpect(status().isAccepted());
        registrations.register(strangers, email.toUpperCase(java.util.Locale.ROOT)).andExpect(status().isAccepted());

        assertThat(usersNamed(invited)).isOne();
        assertThat(usersNamed(strangers)).isZero();
        assertThat(account(invited)).containsEntry("ROLE", role);
        assertThat(emails.to(email)).isEmpty();
        registrations.activate(token, Registrations.PASSWORD).andExpect(status().isNoContent());
        assertThat(account(invited).get("ACTIVATED_AT")).isNotNull();
    }

    /** T-ADM-008's coverage, extended to the credential routes: success and error bodies alike carry no secret. */
    @Test
    void noCredentialRouteResponseCarriesAHashOrASecretField() throws Exception {
        Account target = accounts.user();
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, target.id());
        String username = Registrations.freshUsername();

        for (ResultActions response : List.of(admin.invite(username, Registrations.emailFor(username), "USER"),
                admin.invite(username, Registrations.emailFor(username), "USER"),
                admin.issueReset(target.id()), admin.unlock(target.id(), "OTHER"), admin.unlock(actor.id(), "OTHER"),
                admin.issueReset(UUID.randomUUID()))) {
            String body = response.andReturn().getResponse().getContentAsString();
            assertThat(body).isNotEmpty().doesNotContain("passwordHash", "totpSecret", "totpKey",
                    "emailHmac", "tokenHash", hash);
        }
    }

    @Test
    void onlyAnAdministratorHoldingARecentFactorMayInvite() throws Exception {
        Account user = accounts.user();
        CsrfSession session = SignedIn.as(mockMvc, user);
        String username = Registrations.freshUsername();

        new AdminCredentialCalls(mockMvc, session).invite(username, Registrations.emailFor(username), "USER")
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
        CsrfSession unverified = SignedIn.as(mockMvc, accounts.withRole("ADMIN"));
        new AdminCredentialCalls(mockMvc, unverified).invite(username, Registrations.emailFor(username), "USER")
                .andExpect(status().is4xxClientError());

        assertThat(usersNamed(username)).isZero();
    }
}
