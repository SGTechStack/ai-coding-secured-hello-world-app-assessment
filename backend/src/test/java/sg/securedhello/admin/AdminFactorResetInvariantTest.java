package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.mfa.TotpWindow;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxLockHoldTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

/**
 * The factor reset's exemption from the two-admin count (ADR-049), at exactly two enrolled admins. It needs a database
 * whose admin population it can pin, so it runs in {@code ctx-lockhold} beside {@link TwoAdminInvariantTest}, which
 * owns that database the same way: each test starts from no enrolled admin by deleting every factor row.
 */
class AdminFactorResetInvariantTest extends CtxLockHoldTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private AuthenticableAdmins authenticable;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private TransactionTemplate transactions;

    private Accounts accounts;
    private TotpFactors factors;

    @BeforeEach
    void startFromNoEnrolledAdmin() {
        jdbc.update("DELETE FROM totp_user_details");
        accounts = new Accounts(jdbc, passwordEncoder);
        factors = new TotpFactors(jdbc, cipher, clock);
    }

    /** An enrolled admin, signed in with a freshly verified factor. */
    private record Admin(Account account, CsrfSession session) {

        UUID id() {
            return account.id();
        }
    }

    private Admin enrolledAdmin() throws Exception {
        Account account = accounts.withRole("ADMIN");
        return new Admin(account, factors.verified(mockMvc, SignedIn.as(mockMvc, account), factors.enrol(account)));
    }

    private ResultActions reset(Admin actor, UUID subject) throws Exception {
        return mockMvc.perform(delete("/api/admin/users/" + subject + "/totp").with(actor.session().inHeader()));
    }

    /** The guard's count, read under its own lock set, and the gauge's: they must agree here. */
    private void assertCounts(long expected) {
        Long enrolled = transactions.execute(status -> authenticable.lockForChange(UUID.randomUUID()).stream()
                .filter(AuthenticableAdmins.Standing::enrolledAdmin).count());
        assertThat(enrolled).isEqualTo(expected);
        assertThat(registry.get(AuthenticableAdmins.GAUGE).gauge().value()).isEqualTo((double) expected);
    }

    @Test
    @Proves("T-ADM-027")
    void atExactlyTwoEnrolledAdminsAResetsBsFactor() throws Exception {
        Admin a = enrolledAdmin();
        Admin b = enrolledAdmin();
        assertCounts(2);

        reset(a, b.id()).andExpect(status().isNoContent());

        assertCounts(1);
    }

    @Test
    @Proves("T-ADM-029")
    void aCannotResetTheirOwnFactorAndAfterAResetBReEnrolsUnaidedAndTheCountReturnsToTwo() throws Exception {
        Admin a = enrolledAdmin();
        Admin b = enrolledAdmin();

        reset(a, a.id()).andExpect(problem(ErrorCode.ACCESS_DENIED));
        assertCounts(2);

        reset(a, b.id()).andExpect(status().isNoContent());
        assertCounts(1);

        // B alone: sign in with the password, enrol through the routes, verify. No other admin acts.
        CsrfSession signedIn = SignedIn.as(mockMvc, b.account());
        byte[] secret = factors.enrolThroughRoutes(mockMvc, signedIn);
        clock.advance(Duration.ofSeconds(TotpWindow.STEP_SECONDS));
        factors.verified(mockMvc, SignedIn.as(mockMvc, b.account()), secret);
        assertCounts(2);
    }
}
