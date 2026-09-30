package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.security.device.TrustedDevices;
import sg.securedhello.user.DeviceLockState;

/**
 * Device cookies in the shared {@code dev} contexts (ADR-075), where the cookie is {@link TrustedDevices#DEV_COOKIE}:
 * earning one with a correct password, signing in with one, and reading a device's lane straight from
 * {@code trusted_devices}.
 */
public final class DeviceCookies {

    /** The cookie's name under {@code dev}. */
    public static final String NAME = TrustedDevices.DEV_COOKIE;

    private DeviceCookies() {
    }

    /** Signs {@code account} in from {@code source} with no device cookie and returns the device cookie it earned. */
    public static Cookie earn(MockMvc mockMvc, String source, Accounts.Account account) throws Exception {
        MvcResult result = login(mockMvc, source, null, account.username(), account.password());
        assertThat(result.getResponse().getStatus()).as("sign-in that earns a device cookie").isEqualTo(200);
        Cookie device = result.getResponse().getCookie(NAME);
        assertThat(device).as("device cookie").isNotNull();
        return device;
    }

    /**
     * Posts a login from {@code source} on a freshly bootstrapped session, presenting {@code device} if it is not
     * {@code null}, as a browser holding that cookie would.
     */
    public static MvcResult login(MockMvc mockMvc, String source, @Nullable Cookie device, String username,
            String password) throws Exception {
        CsrfSession session = CsrfSession.bootstrap(mockMvc, source);
        return mockMvc.perform(post("/api/login").with(request -> {
            request.setRemoteAddr(source);
            if (device == null) {
                request.setCookies(session.cookie());
            } else {
                request.setCookies(session.cookie(), device);
            }
            request.addHeader(CsrfSession.HEADER, session.token());
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(SignedIn.credentials(username, password))).andReturn();
    }

    /** The device a cookie names, read from its value without checking the HMAC. */
    public static UUID idOf(Cookie device) {
        ByteBuffer id = ByteBuffer.wrap(Base64.getUrlDecoder().decode(device.getValue().split("\\.", 2)[0]));
        return new UUID(id.getLong(), id.getLong());
    }

    /** The device's lane, read straight from {@code trusted_devices}. */
    public static DeviceLockState state(JdbcTemplate jdbc, Cookie device) {
        return jdbc.queryForObject("SELECT failed_login_attempts, last_failed_at, locked_until, consecutive_failures"
                + " FROM trusted_devices WHERE id = ?", (rs, row) -> new DeviceLockState(rs.getInt(1),
                        instant(rs.getTimestamp(2)), instant(rs.getTimestamp(3)), rs.getInt(4)), idOf(device));
    }

    /** How many trusted devices {@code account} holds. */
    public static int count(JdbcTemplate jdbc, UUID account) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM trusted_devices WHERE user_id = ?", Integer.class,
                account);
        return count == null ? 0 : count;
    }

    private static @Nullable Instant instant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
