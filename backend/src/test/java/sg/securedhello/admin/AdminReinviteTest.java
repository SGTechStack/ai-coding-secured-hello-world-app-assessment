package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AdminCredentialCalls;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.TotpFactors;

/**
 * Re-invite (ADR-007 amendment): {@code POST /api/admin/users} for the identifiers of a pending invite issues that
 * invite again on the same row, cancelling its outstanding token. Only an enabled, never-activated account marked
 * invited by its admin-issued token qualifies; anything else stays {@code USER_EXISTS}.
 */
class AdminReinviteTest extends CtxDefaultTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    private Accounts accounts;
    private Account actor;
    private AdminCredentialCalls admin;
    private Registrations registrations;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new Accounts(jdbc, passwordEncoder);
        actor = accounts.withRole("ADMIN");
        admin = AdminCredentialCalls.signedIn(mockMvc, new TotpFactors(jdbc, cipher, clock), actor);
        registrations = new Registrations(mockMvc);
    }

    private ResultActions setEnabled(UUID id, boolean enabled) throws Exception {
        return mockMvc.perform(put("/api/admin/users/" + id + "/enabled").with(admin.session().inHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":" + enabled + "}"));
    }

    private Map<String, Object> account(String username) {
        return jdbc.queryForMap("SELECT id, role, activated_at FROM users WHERE username = ?", username);
    }

    @Test
    void anExpiredInviteIsReissuedOnTheSameRowAndOnlyTheNewLinkActivates() throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);
        String old = AdminCredentialCalls.token(admin.invite(username, email, "USER").andExpect(status().isCreated()));
        UUID id = (UUID) account(username).get("ID");
        clock.advance(CredentialTokenType.ACTIVATION.lifetime().plusSeconds(1));
        // A day later the first session is long gone: another administrator re-invites.
        Account later = accounts.withRole("ADMIN");
        AdminCredentialCalls laterAdmin = AdminCredentialCalls.signedIn(mockMvc, new TotpFactors(jdbc, cipher, clock),
                later);

        String fresh;
        try (AuditCapture audit = AuditCapture.start()) {
            ResultActions reissued = laterAdmin.invite(username, email.toUpperCase(java.util.Locale.ROOT), "ADMIN")
                    .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"));
            fresh = AdminCredentialCalls.token(reissued);
            assertThat(AdminCredentialCalls.userId(reissued)).isEqualTo(id);
            assertThat(audit.withMessage("Invitation re-issued.")).singleElement().satisfies(row ->
                    assertThat(row).containsEntry("event.action", "user-provisioning")
                            .containsEntry("user.id", later.id().toString())
                            .containsEntry("user.target.id", id.toString()));
            assertThat(audit.withMessage("Account invited.")).isEmpty();
        }

        assertThat(fresh).isNotEqualTo(old);
        assertThat(account(username)).containsEntry("ROLE", "ADMIN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username))
                .isOne();
        registrations.activate(old, Registrations.PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        registrations.activate(fresh, Registrations.PASSWORD).andExpect(status().isNoContent());
        assertThat(account(username).get("ACTIVATED_AT")).isNotNull();
        // Activated now: it is no longer a pending invite.
        laterAdmin.invite(username, email, "ADMIN").andExpect(problem(ErrorCode.USER_EXISTS));
    }

    @Test
    void aPendingInvitesOutstandingTokenIsCancelledByTheReinvite() throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);
        String old = AdminCredentialCalls.token(admin.invite(username, email, "USER").andExpect(status().isCreated()));

        String fresh = AdminCredentialCalls.token(admin.invite(username, email, "USER").andExpect(status().isCreated()));

        registrations.activate(old, Registrations.PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        registrations.activate(fresh, Registrations.PASSWORD).andExpect(status().isNoContent());
    }

    /** A disable cancels the invite's token but keeps its marker: disabled it stays refused, re-enabled it re-invites. */
    @Test
    void aDisabledInviteIsRefusedAndOnceReEnabledIsReinvited() throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);
        String old = AdminCredentialCalls.token(admin.invite(username, email, "USER").andExpect(status().isCreated()));
        UUID id = (UUID) account(username).get("ID");

        setEnabled(id, false).andExpect(status().isOk());
        registrations.activate(old, Registrations.PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        admin.invite(username, email, "USER").andExpect(problem(ErrorCode.USER_EXISTS));

        setEnabled(id, true).andExpect(status().isOk());
        String fresh = AdminCredentialCalls.token(admin.invite(username, email, "USER").andExpect(status().isCreated()));
        registrations.activate(fresh, Registrations.PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void aPendingSelfRegistrationIsNeverReinvitedAndKeepsItsToken() throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);
        registrations.register(username, email).andExpect(status().isAccepted());
        String own = emails.latestToken(email, CredentialTokenType.ACTIVATION).orElseThrow();

        admin.invite(username, email, "ADMIN").andExpect(problem(ErrorCode.USER_EXISTS));

        assertThat(account(username)).containsEntry("ROLE", "USER");
        registrations.activate(own, Registrations.PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void anActiveAccountOrIdentifiersOfTwoAccountsAreRefused() throws Exception {
        Account active = accounts.user();
        String first = Registrations.freshUsername();
        String second = Registrations.freshUsername();
        admin.invite(first, Registrations.emailFor(first), "USER").andExpect(status().isCreated());
        admin.invite(second, Registrations.emailFor(second), "USER").andExpect(status().isCreated());

        admin.invite(active.username(), active.username() + "@example.test", "USER")
                .andExpect(problem(ErrorCode.USER_EXISTS));
        // One invite's username with the other's address, or with a new address: not one invite, so refused.
        admin.invite(first, Registrations.emailFor(second), "USER").andExpect(problem(ErrorCode.USER_EXISTS));
        admin.invite(first, Registrations.emailFor(Registrations.freshUsername()), "USER")
                .andExpect(problem(ErrorCode.USER_EXISTS));
    }
}
