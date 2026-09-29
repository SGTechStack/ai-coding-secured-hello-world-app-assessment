package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.LogOutputGuard;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TestSecrets;

/**
 * The canary-secret scan over a real context (T-AUD-013): after startup, and after requests that carry the canaries
 * in a header, the query string and a body, no canary has reached stdout, stderr or the audit file in any encoded
 * form. {@link LogOutputGuard} repeats the same scan after every test in the suite.
 */
class CanarySecretScanTest extends CtxDefaultTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    @Proves("T-AUD-013")
    void noCanaryReachesAnyAppenderAcrossStartupAndRequestsThatCarryThem() throws Exception {
        String basic = Base64.getEncoder().encodeToString((TestSecrets.ADMIN_USERNAME + ":"
                + TestSecrets.ADMIN_PASSWORD).getBytes(StandardCharsets.UTF_8));
        LogOutputGuard.register("server-secret-registered-by-this-test");

        mockMvc.perform(get("/api/hello").queryParam("password", TestSecrets.ADMIN_PASSWORD)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"canary-admin\",\"password\":\"" + TestSecrets.ADMIN_PASSWORD + "\"}"))
                .andExpect(status().is4xxClientError());

        assertThat(LogOutputGuard.capturesStandardStreams()).isTrue();
        assertThat(LogOutputGuard.scanNow(false)).isEmpty();
    }

    /**
     * The login path is the one place a submitted password is parsed, authenticated, turned into a failure event and
     * audited. The canary is sent with a real CSRF session, so the request gets past {@code CsrfFilter} and the audit
     * rows prove the login code ran; the success path uses the fixture password, which the guard scans for suite-wide.
     */
    @Test
    @Proves("T-AUD-013")
    void aCanaryPasswordThatReachesTheLoginCodeReachesNoAppender() throws Exception {
        String canaryPassword = "TEST-ONLY-CANARY-submitted-login-password-5e21";
        LogOutputGuard.register(canaryPassword);
        Account account = new Accounts(jdbc, passwordEncoder).user();

        try (AuditCapture audit = AuditCapture.start()) {
            SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), account.username(), canaryPassword)
                    .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
            assertThat(audit.withMessage("Login failed.")).as("the provider and the failure audit ran").hasSize(1);

            SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), account.username(), account.password())
                    .andExpect(status().isOk());
            assertThat(audit.withMessage("Login succeeded.")).as("the success path ran").hasSize(1);
        }

        assertThat(LogOutputGuard.scanNow(false)).isEmpty();
    }
}
