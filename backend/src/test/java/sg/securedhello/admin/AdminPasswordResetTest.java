package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.credential.CredentialTokenHash;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.credential.CredentialTokens;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AdminCredentialCalls;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.PasswordResets;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;
import sg.securedhello.user.PasswordLockoutState;

/**
 * {@code POST /api/admin/users/{uuid}/password-reset} (ADR-006; ADR-037): a reset token returned once, for any
 * activated, enabled account including the admin's own, redeemed at the ordinary confirm. Issuing it ends the
 * subject's sessions and clears nothing (REJ-016; R-LCK-010). While it is pending, a self-service request leaves it in
 * place and answers exactly as for any other address (ADR-007 amendment).
 */
class AdminPasswordResetTest extends CtxDefaultTest {

    private static final String ISSUED = "Password reset token issued by an administrator.";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private CredentialTokens tokens;

    private Accounts accounts;
    private Account actor;
    private AdminCredentialCalls admin;
    private PasswordResets resets;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new Accounts(jdbc, passwordEncoder);
        actor = accounts.withRole("ADMIN");
        admin = AdminCredentialCalls.signedIn(mockMvc, new TotpFactors(jdbc, cipher, clock), actor);
        resets = new PasswordResets(mockMvc);
    }

    private String issue(UUID id) throws Exception {
        return AdminCredentialCalls.token(admin.issueReset(id).andExpect(status().isOk()));
    }

    private int login(Account account, String password) throws Exception {
        return SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), account.username(), password).andReturn()
                .getResponse().getStatus();
    }

    private void lock(Account account, Instant until) {
        jdbc.update("UPDATE users SET failed_login_attempts = 5, last_failed_at = ?, locked_until = ?,"
                + " consecutive_failures_since_success = 5 WHERE id = ?", Timestamp.from(clock.instant()),
                Timestamp.from(until), account.id());
    }

    @Test
    @Proves("T-CRED-010")
    void theTokenIsReturnedOnceUncachedDiffersOnEachIssuanceAndRedeemsAtConfirm() throws Exception {
        Account target = accounts.user();

        String first;
        String second;
        try (AuditCapture audit = AuditCapture.start()) {
            first = AdminCredentialCalls.token(admin.issueReset(target.id()).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store")));
            second = issue(target.id());
            assertThat(audit.withMessage(ISSUED)).hasSize(2).allSatisfy(row ->
                    assertThat(row).containsEntry("event.action", "password-reset")
                            .containsEntry("user.id", actor.id().toString())
                            .containsEntry("user.target.id", target.id().toString()));
            assertThat(audit.rows()).allSatisfy(row -> assertThat(row.values()).doesNotContain(first, second));
        }

        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(second).hasSize(43).matches("[A-Za-z0-9_-]+").isNotEqualTo(first);
        assertThat(jdbc.queryForMap("SELECT type, admin_issued FROM credential_tokens WHERE token_hash = ?",
                CredentialTokenHash.hash(CredentialTokenType.PASSWORD_RESET, second)))
                .containsEntry("TYPE", "PASSWORD_RESET").containsEntry("ADMIN_ISSUED", true);
        // No endpoint returns a plaintext again: the account's own admin read carries neither token.
        String read = mockMvc.perform(get("/api/admin/users/" + target.id()).cookie(admin.session().cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(read).doesNotContain(first, second, "token");
        // The second issuance replaced the first; the second redeems at the ordinary confirm.
        resets.confirm(first, PasswordResets.NEW_PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        resets.confirm(second, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(login(target, PasswordResets.NEW_PASSWORD)).isEqualTo(200);
    }

    @Test
    void issuingEndsTheSubjectsSessionsButClearsNoLockWhichOnlyTheRedemptionClears() throws Exception {
        Account target = accounts.user();
        CsrfSession live = SignedIn.as(mockMvc, target);
        lock(target, clock.instant().plus(Duration.ofMinutes(20)));
        PasswordLockoutState locked = accounts.lockoutState(target);

        String token = issue(target.id());

        mockMvc.perform(get("/api/hello").cookie(live.cookie())).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        assertThat(accounts.lockoutState(target)).as("issuance clears nothing (REJ-016)").isEqualTo(locked);
        assertThat(login(target, target.password())).as("still locked").isEqualTo(401);

        resets.confirm(token, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(accounts.lockoutState(target)).isEqualTo(PasswordLockoutState.CLEAR);
        assertThat(login(target, PasswordResets.NEW_PASSWORD)).isEqualTo(200);
    }

    /** ADR-006: no self-action rule; the admin's own sessions end with the issuance, this one included. */
    @Test
    void anAdministratorMayIssueAResetForTheirOwnAccount() throws Exception {
        String token = issue(actor.id());

        mockMvc.perform(get("/api/admin/users").cookie(admin.session().cookie()))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        resets.confirm(token, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(login(actor, PasswordResets.NEW_PASSWORD)).isEqualTo(200);
    }

    /** R-STD-024: a reset token is minted only for an activated, enabled account; an unknown one is not found. */
    @Test
    void aPendingOrDisabledAccountIsRefusedAndAnUnknownOneIsNotFound() throws Exception {
        Account pending = accounts.notActivated();
        Account disabled = accounts.disabled();

        admin.issueReset(pending.id()).andExpect(problem(ErrorCode.VALIDATION_FAILED));
        admin.issueReset(disabled.id()).andExpect(problem(ErrorCode.VALIDATION_FAILED));
        admin.issueReset(UUID.randomUUID()).andExpect(problem(ErrorCode.ACCESS_DENIED));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credential_tokens WHERE user_id IN (?, ?)",
                Integer.class, pending.id(), disabled.id())).isZero();
    }

    /**
     * The ADR-007 amendment: while an admin-issued reset token is pending, a self-service request mints nothing and
     * leaves it in place, and its response is that of a request for an address no account has.
     */
    @Test
    void aSelfServiceRequestLeavesAPendingAdminTokenInPlaceAndAnswersAsForAnyAddress() throws Exception {
        Account target = accounts.user();
        String email = PasswordResets.emailOf(target);
        String token = issue(target.id());

        // The audit row is one keyed row that never names an account (REJ-002), whatever the address's state.
        MockHttpServletResponse held = resets.request(email).andReturn().getResponse();
        MockHttpServletResponse unknown = resets.request(Registrations.emailFor(Registrations.freshUsername()))
                .andReturn().getResponse();

        assertThat(held.getStatus()).isEqualTo(202).isEqualTo(unknown.getStatus());
        assertThat(held.getContentAsString()).isEqualTo(unknown.getContentAsString()).isEmpty();
        assertThat(held.getHeaderNames()).containsExactlyInAnyOrderElementsOf(unknown.getHeaderNames());
        assertThat(emails.to(email)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credential_tokens WHERE user_id = ?", Integer.class,
                target.id())).isOne();
        resets.confirm(token, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());
    }

    /**
     * A self-service issuance that raced past the pending check, after the admin issued, still cannot cancel the admin
     * token: the self-service path deletes only self-issued tokens, so the guarantee holds atomically.
     */
    @Test
    void aSelfServiceIssuanceNeverDeletesAnAdminToken() throws Exception {
        Account target = accounts.user();
        String admins = issue(target.id());

        String own = tokens.mint(target.id(), CredentialTokenType.PASSWORD_RESET);

        resets.confirm(admins, PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());
        // The password set then cancels every pending reset token, the self-issued one included (ADR-007).
        resets.confirm(own, "another copper lantern drifts east").andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
    }

    /** The hold ends with the admin token: once it expires or is redeemed, a self-service request works again. */
    @Test
    void onceTheAdminTokenIsNoLongerPendingASelfServiceRequestIssuesAgain() throws Exception {
        Account expired = accounts.user();
        Account redeemed = accounts.user();
        issue(expired.id());
        resets.confirm(issue(redeemed.id()), PasswordResets.NEW_PASSWORD).andExpect(status().isNoContent());

        clock.advance(CredentialTokenType.PASSWORD_RESET.lifetime().plusSeconds(1));
        for (Account account : new Account[] {expired, redeemed}) {
            resets.request(PasswordResets.emailOf(account)).andExpect(status().isAccepted());
            assertThat(emails.latestToken(PasswordResets.emailOf(account), CredentialTokenType.PASSWORD_RESET))
                    .isPresent();
        }
        // The expired admin token's row stays; the new pending one beside it is self-issued.
        assertThat(jdbc.queryForList("SELECT admin_issued FROM credential_tokens WHERE user_id = ?"
                + " AND used_at IS NULL ORDER BY created_at", Boolean.class, expired.id())).containsExactly(true, false);
    }
}
