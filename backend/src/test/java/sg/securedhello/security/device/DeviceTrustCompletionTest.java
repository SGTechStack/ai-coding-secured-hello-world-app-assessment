package sg.securedhello.security.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.security.lockout.LockoutProperties;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.DeviceCookies;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

import tools.jackson.databind.json.JsonMapper;

/**
 * When a browser becomes a trusted device, and what sign-out leaves of it (ADR-075). A device is trusted only once a
 * sign-in is complete: at the correct password for an account that needs no second factor, and at the verified code
 * for an administrator's, so a password alone neither trusts nor evicts an administrator's device. Sign-out expires the
 * session cookie and leaves the device cookie alone.
 */
class DeviceTrustCompletionTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String OWNER = "198.51.100.35";
    private static final String ATTACKER = "203.0.113.35";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private LockoutProperties lockout;

    private Accounts accounts;
    private TotpFactors factors;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        factors = new TotpFactors(jdbc, cipher, clock);
    }

    private static List<String> deviceHeaders(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(DeviceCookies.NAME + "=")).toList();
    }

    private int status(String source, Cookie device, Account account, String password) throws Exception {
        return DeviceCookies.login(mockMvc, source, device, account.username(), password).getResponse().getStatus();
    }

    /** Signs {@code admin} in by password from {@code source}, presenting {@code device}, and returns the session. */
    private CsrfSession passwordOnly(String source, Cookie device, Account admin) throws Exception {
        MvcResult login = DeviceCookies.login(mockMvc, source, device, admin.username(), admin.password());
        assertThat(login.getResponse().getStatus()).as("the administrator's correct password").isEqualTo(200);
        assertThat(deviceHeaders(login)).as("a password alone earns an administrator no device").isEmpty();
        return SignedIn.refreshed(mockMvc, login.getResponse().getCookie("SESSION"));
    }

    /** Verifies the current code on {@code session}, presenting {@code device}, on a fresh TOTP step. */
    private MvcResult verify(CsrfSession session, Cookie device, byte[] secret) throws Exception {
        clock.advance(Duration.ofSeconds(30));
        String body = JSON.writeValueAsString(Map.of("code", factors.code(secret)));
        MvcResult result = mockMvc.perform(post(TotpFactors.VERIFICATION).with(request -> {
            request.setRemoteAddr(OWNER);
            if (device == null) {
                request.setCookies(session.cookie());
            } else {
                request.setCookies(session.cookie(), device);
            }
            request.addHeader(CsrfSession.HEADER, session.token());
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(result.getResponse().getStatus()).as("verification").isEqualTo(204);
        return result;
    }

    @Test
    @Proves("T-LCK-033")
    void signOutExpiresTheSessionCookieAndLeavesTheDeviceCookieSoTheOwnerStillPassesAnUntrustedLock()
            throws Exception {
        Account owner = accounts.user();
        MvcResult login = DeviceCookies.login(mockMvc, OWNER, null, owner.username(), owner.password());
        Cookie device = login.getResponse().getCookie(DeviceCookies.NAME);
        assertThat(device).as("the device cookie the sign-in earned").isNotNull();
        CsrfSession session = SignedIn.refreshed(mockMvc, login.getResponse().getCookie("SESSION"));

        MvcResult logout = mockMvc.perform(post("/api/logout").with(request -> {
            request.setCookies(session.cookie(), device);
            request.addHeader(CsrfSession.HEADER, session.token());
            return request;
        })).andReturn();

        assertThat(logout.getResponse().getStatus()).isEqualTo(204);
        assertThat(logout.getResponse().getHeader("Clear-Site-Data")).isEqualTo("\"cache\", \"storage\"");
        assertThat(logout.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).as("only the session cookie is expired")
                .isNotEmpty().allSatisfy(header -> assertThat(header).startsWith("SESSION=;")
                        .contains("Max-Age=0", "Path=/", "HttpOnly", "SameSite=Strict"));
        assertThat(DeviceCookies.count(jdbc, owner.id())).as("the device is still trusted").isOne();

        for (int i = 0; i < lockout.threshold(); i++) {
            assertThat(status(ATTACKER, null, owner, Accounts.WRONG_PASSWORD)).isEqualTo(401);
        }
        assertThat(status(ATTACKER, null, owner, owner.password())).as("the untrusted lane is locked")
                .isEqualTo(401);
        assertThat(status(OWNER, device, owner, owner.password())).as("the signed-out owner's device")
                .isEqualTo(200);
    }

    @Test
    @Proves("T-LCK-034")
    void anAdministratorsBrowserIsTrustedOnlyAtTheVerifiedCodeAndOnlyOnce() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);

        CsrfSession session = passwordOnly(OWNER, null, admin);
        assertThat(DeviceCookies.count(jdbc, admin.id())).as("no trusted device at the password").isZero();

        MvcResult verified = verify(session, null, secret);
        assertThat(deviceHeaders(verified)).singleElement().satisfies(header -> assertThat(header)
                .contains("Path=/", "HttpOnly", "SameSite=Strict", "Max-Age=" + lockout.device().ttl().toSeconds()));
        Cookie device = verified.getResponse().getCookie(DeviceCookies.NAME);
        assertThat(jdbc.queryForObject("SELECT user_id FROM trusted_devices WHERE id = ?", UUID.class,
                DeviceCookies.idOf(device))).isEqualTo(admin.id());

        // A renewal from the now-trusted browser, and its next full sign-in, earn no second device.
        CsrfSession rotated = SignedIn.refreshed(mockMvc, verified.getResponse().getCookie("SESSION"));
        assertThat(deviceHeaders(verify(rotated, device, secret))).isEmpty();
        assertThat(deviceHeaders(verify(passwordOnly(OWNER, device, admin), device, secret))).isEmpty();
        assertThat(DeviceCookies.count(jdbc, admin.id())).isOne();

        // A regular user's correct password still earns one there and then.
        Account user = accounts.user();
        assertThat(deviceHeaders(DeviceCookies.login(mockMvc, OWNER, null, user.username(), user.password())))
                .hasSize(1);
    }

    @Test
    @Proves("T-LCK-035")
    void aPasswordOnlyAttackerCannotEvictAnAdministratorsDevices() throws Exception {
        Account admin = accounts.withRole("ADMIN");
        byte[] secret = factors.enrol(admin);
        List<Cookie> owned = new ArrayList<>();
        for (int i = 0; i < TrustedDevices.MAXIMUM_PER_ACCOUNT; i++) {
            MvcResult verified = verify(passwordOnly(OWNER, null, admin), null, secret);
            owned.add(verified.getResponse().getCookie(DeviceCookies.NAME));
        }
        assertThat(DeviceCookies.count(jdbc, admin.id())).isEqualTo(TrustedDevices.MAXIMUM_PER_ACCOUNT);

        for (int i = 0; i < 3; i++) {
            passwordOnly(ATTACKER, null, admin);
        }

        assertThat(DeviceCookies.count(jdbc, admin.id())).isEqualTo(TrustedDevices.MAXIMUM_PER_ACCOUNT);
        for (Cookie device : owned) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trusted_devices WHERE id = ?", Integer.class,
                    DeviceCookies.idOf(device))).as("every owned device is still trusted").isOne();
        }
    }
}
