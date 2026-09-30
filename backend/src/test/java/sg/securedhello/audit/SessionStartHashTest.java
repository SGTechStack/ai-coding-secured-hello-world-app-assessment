package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;

/**
 * The audit session strategy's place in the login composite (ADR-038): row 8, the session start, is written before the
 * session id rotates, so it carries the pre-rotation {@code session.hash} that joins the sign-in to the rows the same
 * anonymous session wrote before it; row 1, the login success, is written after, with the new hash.
 */
class SessionStartHashTest extends CtxDefaultTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static String hashOf(AuditCapture audit, String message) {
        Map<String, Object> row = audit.withMessage(message).getLast();
        return (String) row.get("session.hash");
    }

    @Test
    @Proves("T-AUD-015")
    void theSessionStartRowCarriesThePreRotationHashThatJoinsTheEarlierRows() throws Exception {
        Account account = new Accounts(jdbc, passwordEncoder).user();
        CsrfSession anonymous = CsrfSession.bootstrap(mockMvc);

        try (AuditCapture audit = AuditCapture.start()) {
            SignedIn.login(mockMvc, anonymous, account.username(), Accounts.WRONG_PASSWORD);
            SignedIn.login(mockMvc, anonymous, account.username(), account.password()).andExpect(status().isOk());

            String beforeSignIn = hashOf(audit, "Login failed.");
            String sessionStart = hashOf(audit, "Session started.");
            String loginSuccess = hashOf(audit, "Login succeeded.");
            assertThat(beforeSignIn).matches("[0-9a-f]{64}");
            assertThat(sessionStart).as("row 8 joins the pre-login rows").isEqualTo(beforeSignIn);
            assertThat(loginSuccess).as("row 1 carries the rotated session").isNotEqualTo(sessionStart)
                    .matches("[0-9a-f]{64}");
        }
    }
}
