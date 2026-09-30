package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AdminCredentialCalls;
import sg.securedhello.testsupport.CtxLockHoldTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.TotpFactors;

/**
 * An invited, unredeemed administrator never counts toward the two-admin invariant (ADR-006; ADR-048), created here
 * through the real invite route. Like {@link TwoAdminInvariantTest}, it runs in {@code ctx-lockhold}, whose database
 * no shared test enrols admins in, and starts each test from no enrolled admin.
 */
class InvitedAdminNotCountedTest extends CtxLockHoldTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    private Accounts accounts;
    private TotpFactors factors;

    @BeforeEach
    void startFromNoEnrolledAdmin() {
        jdbc.update("DELETE FROM totp_user_details");
        accounts = new Accounts(jdbc, passwordEncoder);
        factors = new TotpFactors(jdbc, cipher, clock);
    }

    private ResultActions disable(AdminCredentialCalls actor, UUID subject) throws Exception {
        return mockMvc.perform(put("/api/admin/users/" + subject + "/enabled").with(actor.session().inHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"));
    }

    @Test
    @Proves("T-ADM-010")
    void anInvitedButUnredeemedAdminDoesNotMakeTheSecondOfTwo() throws Exception {
        Account first = accounts.withRole("ADMIN");
        AdminCredentialCalls a = AdminCredentialCalls.signedIn(mockMvc, factors, first);
        Account second = accounts.withRole("ADMIN");
        AdminCredentialCalls b = AdminCredentialCalls.signedIn(mockMvc, factors, second);
        String username = Registrations.freshUsername();
        UUID invited = AdminCredentialCalls.userId(a.invite(username, Registrations.emailFor(username), "ADMIN")
                .andExpect(status().isCreated()));
        // Even holding a factor row, the invite is not activated, so it is not an enrolled admin.
        factors.enrol(new Account(invited, username, null));

        // One real admin plus an invite are not two: neither real admin can be disabled, nor disable themselves.
        disable(a, second.id()).andExpect(problem(ErrorCode.TWO_ADMIN_INVARIANT));
        disable(b, first.id()).andExpect(problem(ErrorCode.TWO_ADMIN_INVARIANT));
        disable(a, first.id()).andExpect(problem(ErrorCode.ACCESS_DENIED));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id IN (?, ?) AND enabled", Integer.class,
                first.id(), second.id())).isEqualTo(2);
        disable(a, invited).andExpect(status().isOk());
    }
}
