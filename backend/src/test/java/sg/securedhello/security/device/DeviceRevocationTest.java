package sg.securedhello.security.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.password.PasswordService;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AdminCredentialCalls;
import sg.securedhello.testsupport.CapturedEmails;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.DeviceCookies;
import sg.securedhello.testsupport.PasswordResets;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

import tools.jackson.databind.json.JsonMapper;

/**
 * Every credential change and every admin action that ends an account's standing revokes all of its trusted devices,
 * so no device cookie issued before it keeps its own lockout lane (ADR-075): a self-service change, a reset
 * redemption, an admin-issued reset, an admin disable, a delete, and the recovery runner's two password forms.
 */
class DeviceRevocationTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SOURCE = "198.51.100.132";
    private static final String NEW_PASSWORD = "copper lantern drifts westward";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private PasswordService passwords;

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
    }

    /** Two trusted devices for {@code account}, as two browsers that each entered its password. */
    private Cookie twoDevices(Account account) throws Exception {
        Cookie first = DeviceCookies.earn(mockMvc, SOURCE, account);
        DeviceCookies.earn(mockMvc, SOURCE, account);
        assertThat(DeviceCookies.count(jdbc, account.id())).isEqualTo(2);
        return first;
    }

    private void assertRevoked(Account account) {
        assertThat(DeviceCookies.count(jdbc, account.id())).as("every trusted device is revoked").isZero();
    }

    private AdminCredentialCalls admin() throws Exception {
        TotpFactors factors = new TotpFactors(jdbc, cipher, clock);
        return AdminCredentialCalls.signedIn(mockMvc, factors, accounts.withRole("ADMIN"));
    }

    @Test
    @Proves("T-CRED-032")
    void aSelfServicePasswordChangeRevokesEveryDevice() throws Exception {
        Account account = accounts.user();
        twoDevices(account);
        CsrfSession session = SignedIn.as(mockMvc, account);

        mockMvc.perform(patch("/api/profile/password").with(session.inHeader()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("currentPassword", account.password(),
                        "newPassword", NEW_PASSWORD)))).andExpect(status().is2xxSuccessful());

        assertRevoked(account);
    }

    @Test
    @Proves("T-CRED-032")
    void aResetRedemptionRevokesEveryDevice() throws Exception {
        Account account = accounts.user();
        twoDevices(account);
        PasswordResets resets = new PasswordResets(mockMvc, SOURCE);
        resets.request(PasswordResets.emailOf(account)).andExpect(status().isAccepted());

        resets.confirm(CapturedEmails.shared().latestToken(PasswordResets.emailOf(account),
                CredentialTokenType.PASSWORD_RESET).orElseThrow(), NEW_PASSWORD)
                .andExpect(status().is2xxSuccessful());

        assertRevoked(account);
    }

    @Test
    @Proves("T-CRED-032")
    void anAdminIssuedResetRevokesEveryDevice() throws Exception {
        Account account = accounts.user();
        twoDevices(account);

        admin().issueReset(account.id()).andExpect(status().isOk());

        assertRevoked(account);
    }

    @Test
    @Proves("T-CRED-032")
    void anAdminDisableAndADeleteRevokeEveryDevice() throws Exception {
        Account disabled = accounts.user();
        Account deleted = accounts.user();
        twoDevices(disabled);
        twoDevices(deleted);
        CsrfSession admin = admin().session();

        mockMvc.perform(put("/api/admin/users/" + disabled.id() + "/enabled").with(admin.inHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/users/" + deleted.id()).with(admin.inHeader()))
                .andExpect(status().is2xxSuccessful());

        assertRevoked(disabled);
        assertRevoked(deleted);
    }

    /** The recovery runner sets or invalidates the password through these two, and nothing else (ADR-073). */
    @Test
    @Proves("T-CRED-032")
    void theRecoveryRunnersCredentialResetsRevokeEveryDevice() throws Exception {
        Account issued = accounts.user();
        Account invalidated = accounts.user();
        twoDevices(issued);
        twoDevices(invalidated);

        passwords.issueForcedChangeCredential(issued.id(), NEW_PASSWORD);
        passwords.invalidate(invalidated.id());

        assertRevoked(issued);
        assertRevoked(invalidated);
    }
}
