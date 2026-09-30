package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

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
 * An invite of identifiers a self-registered pending registration holds (ADR-006; ADR-032 amendment). Once that
 * registration has lapsed, 24 hours after it was last registered, it no longer holds them: the invite deletes it
 * without a tombstone, writes the lapse row a registration would (row 48) and proceeds. Until then it still refuses
 * with {@code USER_EXISTS} and changes nothing.
 */
class AdminInviteLapseTest extends CtxDefaultTest {

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

    /** A newly signed-in, factor-verified admin: the shared clock has moved on past any earlier session's factor. */
    private AdminCredentialCalls admin() throws Exception {
        return AdminCredentialCalls.signedIn(mockMvc, new TotpFactors(jdbc, cipher, clock),
                new Accounts(jdbc, passwordEncoder).withRole("ADMIN"));
    }

    /** Which of a self-registration's identifiers the invite reuses. */
    private enum Reused {
        USERNAME, EMAIL, BOTH
    }

    private record Squatter(String username, String email, UUID id) {
    }

    private Squatter selfRegistration() throws Exception {
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);
        registrations.register(username, email).andExpect(status().isAccepted());
        return new Squatter(username, email, jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class,
                email));
    }

    private ResultActions inviteReusing(Reused reused, Squatter squatter) throws Exception {
        String fresh = Registrations.freshUsername();
        AdminCredentialCalls admin = admin();
        return switch (reused) {
            case USERNAME -> admin.invite(squatter.username(), Registrations.emailFor(fresh), "USER");
            case EMAIL -> admin.invite(fresh, squatter.email(), "USER");
            case BOTH -> admin.invite(squatter.username(), squatter.email(), "ADMIN");
        };
    }

    private int rows(UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, id);
    }

    @Test
    @Proves("T-ADM-035")
    void aLapsedSelfRegistrationNoLongerHoldsItsIdentifiersAgainstAnInvite() throws Exception {
        for (Reused reused : Reused.values()) {
            Squatter squatter = selfRegistration();
            clock.advance(CredentialTokenType.ACTIVATION.lifetime());

            UUID invited;
            try (AuditCapture audit = AuditCapture.start()) {
                invited = AdminCredentialCalls.userId(inviteReusing(reused, squatter)
                        .andExpect(status().isCreated()));
                assertThat(audit.withMessage(LAPSED)).as(reused.name()).singleElement()
                        .satisfies(row -> assertThat(row).containsEntry("user.id", squatter.id().toString()));
                assertThat(audit.withMessage("Account invited.")).as(reused.name()).hasSize(1);
            }
            assertThat(rows(squatter.id())).as("%s: the lapsed registration is gone", reused).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deleted_users WHERE user_id = ?", Integer.class,
                    squatter.id())).as("no tombstone").isZero();
            assertThat(invited).isNotEqualTo(squatter.id());
            assertThat(rows(invited)).isOne();
        }
    }

    /**
     * Two different lapsed holders, a lapsed invite with the username and a lapsed self-registration with the address:
     * not a re-invite, so both are freed, each with its own lapse row, and the invite is a new account.
     */
    @Test
    @Proves("T-ADM-035")
    void anInviteFreesALapsedInviteAndALapsedSelfRegistrationTogether() throws Exception {
        String invitedName = Registrations.freshUsername();
        UUID lapsedInvite = AdminCredentialCalls.userId(admin().invite(invitedName,
                Registrations.emailFor(invitedName), "ADMIN").andExpect(status().isCreated()));
        Squatter squatter = selfRegistration();
        clock.advance(CredentialTokenType.ACTIVATION.lifetime());

        try (AuditCapture audit = AuditCapture.start()) {
            UUID invited = AdminCredentialCalls.userId(admin().invite(invitedName, squatter.email(), "USER")
                    .andExpect(status().isCreated()));
            assertThat(invited).isNotIn(lapsedInvite, squatter.id());
            assertThat(audit.withMessage(LAPSED)).extracting(row -> row.get("user.id"))
                    .containsExactlyInAnyOrder(lapsedInvite.toString(), squatter.id().toString());
        }
        assertThat(rows(lapsedInvite)).isZero();
        assertThat(rows(squatter.id())).isZero();
    }

    @Test
    @Proves("T-ADM-035")
    void aLiveSelfRegistrationStillRefusesTheInviteAndKeepsItsToken() throws Exception {
        for (Reused reused : Reused.values()) {
            Squatter squatter = selfRegistration();
            clock.advance(CredentialTokenType.ACTIVATION.lifetime().minusSeconds(1));

            try (AuditCapture audit = AuditCapture.start()) {
                inviteReusing(reused, squatter).andExpect(problem(ErrorCode.USER_EXISTS));
                assertThat(audit.withMessage(LAPSED)).as(reused.name()).isEmpty();
            }
            assertThat(rows(squatter.id())).as(reused.name()).isOne();
            registrations.activate(emails.latestToken(squatter.email(), CredentialTokenType.ACTIVATION)
                    .orElseThrow(), Registrations.PASSWORD).andExpect(status().isNoContent());
        }
    }
}
