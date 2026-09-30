package sg.securedhello.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.AdminCredentialCalls;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.TotpFactors;

/**
 * An administrator's invite lapses like a self-registration once its admin-issued activation token has expired
 * unredeemed (ADR-007 amendment of 2026-09-30). Before that, the invitee's own registration of the address creates
 * nothing and another address cannot take its username; after it, the invitee may register themselves, and another
 * address may take the username, each deleting the lapsed invite with the lapse row. A disabled invite never lapses.
 */
class InviteLapseTest extends CtxDefaultTest {

    private static final String LAPSED = "Lapsed pending registration deleted.";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    private Registrations registrations;

    @BeforeEach
    void setUp() {
        registrations = new Registrations(mockMvc);
    }

    /** A newly signed-in, factor-verified admin: the shared clock moves on past any earlier session's factor. */
    private AdminCredentialCalls admin() throws Exception {
        return AdminCredentialCalls.signedIn(mockMvc, new TotpFactors(jdbc, cipher, clock),
                new Accounts(jdbc, passwordEncoder).withRole("ADMIN"));
    }

    private record Invite(String username, String email, UUID id) {
    }

    private Invite invite(String role) throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);
        UUID id = AdminCredentialCalls.userId(admin().invite(username, email, role).andExpect(status().isCreated()));
        return new Invite(username, email, id);
    }

    private Map<String, Object> accountAt(String email) {
        return jdbc.queryForMap("SELECT id, username, role FROM users WHERE email = ?", email);
    }

    private int rows(UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, id);
    }

    @Test
    @Proves("T-CRED-030")
    void anExpiredInviteLetsTheInviteeRegisterThemselvesAsAUser() throws Exception {
        Invite invite = invite("ADMIN");

        clock.advance(CredentialTokenType.ACTIVATION.lifetime().minusSeconds(1));
        registrations.register(invite.username(), invite.email()).andExpect(problem(ErrorCode.VALIDATION_FAILED));
        registrations.register(Registrations.freshUsername(), invite.email()).andExpect(status().isAccepted());
        assertThat(accountAt(invite.email())).as("a live invite is left alone")
                .containsEntry("ID", invite.id()).containsEntry("ROLE", "ADMIN");
        assertThat(emails.latestToken(invite.email(), CredentialTokenType.ACTIVATION)).isEmpty();

        clock.advance(java.time.Duration.ofSeconds(1));
        try (AuditCapture audit = AuditCapture.start()) {
            registrations.register(invite.username(), invite.email()).andExpect(status().isAccepted());
            assertThat(audit.withMessage(LAPSED)).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("user.id", invite.id().toString()));
        }
        assertThat(rows(invite.id())).as("the lapsed invite is gone").isZero();
        Map<String, Object> registered = accountAt(invite.email());
        assertThat(registered).containsEntry("USERNAME", invite.username()).containsEntry("ROLE", "USER");
        assertThat(registered.get("ID")).isNotEqualTo(invite.id());
        registrations.activate(emails.latestToken(invite.email(), CredentialTokenType.ACTIVATION).orElseThrow(),
                Registrations.PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @Proves("T-CRED-030")
    void anExpiredInvitesUsernamePassesToAnotherAddress() throws Exception {
        Invite invite = invite("USER");
        String other = Registrations.emailFor(Registrations.freshUsername());

        clock.advance(CredentialTokenType.ACTIVATION.lifetime().minusSeconds(1));
        registrations.register(invite.username(), other).andExpect(problem(ErrorCode.VALIDATION_FAILED));

        clock.advance(java.time.Duration.ofSeconds(1));
        try (AuditCapture audit = AuditCapture.start()) {
            registrations.register(invite.username(), other).andExpect(status().isAccepted());
            assertThat(audit.withMessage(LAPSED)).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("user.id", invite.id().toString()));
        }
        assertThat(rows(invite.id())).isZero();
        assertThat(accountAt(other)).containsEntry("USERNAME", invite.username());
    }

    /** The disable expired the invite's token in place; lapsing would undo the administrator's decision. */
    @Test
    @Proves("T-CRED-030")
    void aDisabledInviteNeverLapses() throws Exception {
        Invite invite = invite("USER");
        AdminCredentialCalls admin = admin();
        mockMvc.perform(put("/api/admin/users/" + invite.id() + "/enabled").with(admin.session().inHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        clock.advance(CredentialTokenType.ACTIVATION.lifetime().multipliedBy(2));

        try (AuditCapture audit = AuditCapture.start()) {
            registrations.register(Registrations.freshUsername(), invite.email()).andExpect(status().isAccepted());
            registrations.register(invite.username(), Registrations.emailFor(Registrations.freshUsername()))
                    .andExpect(problem(ErrorCode.VALIDATION_FAILED));
            assertThat(audit.withMessage(LAPSED)).isEmpty();
        }
        assertThat(accountAt(invite.email())).containsEntry("ID", invite.id());
    }

    /** Lapsing changes nothing for the administrator: an expired invite is still re-issued on its own row. */
    @Test
    @Proves("T-CRED-030")
    void anExpiredInviteIsStillReinvitedOnItsOwnRow() throws Exception {
        Invite invite = invite("USER");
        clock.advance(CredentialTokenType.ACTIVATION.lifetime().multipliedBy(2));

        try (AuditCapture audit = AuditCapture.start()) {
            UUID reinvited = AdminCredentialCalls.userId(admin().invite(invite.username(), invite.email(), "USER")
                    .andExpect(status().isCreated()));
            assertThat(reinvited).isEqualTo(invite.id());
            assertThat(audit.withMessage("Invitation re-issued.")).hasSize(1);
            assertThat(audit.withMessage(LAPSED)).isEmpty();
        }
    }
}
