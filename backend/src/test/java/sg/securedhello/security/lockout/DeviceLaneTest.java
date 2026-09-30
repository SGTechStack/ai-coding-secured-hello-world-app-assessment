package sg.securedhello.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AuditCapture;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.DeviceCookies;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.user.DeviceLockState;
import sg.securedhello.user.PasswordLockoutState;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The two lockout lanes through the real sign-in (ADR-075): a correct password earns a device cookie; a sign-in that
 * presents a valid one for its username counts in that device's own lane, and every other sign-in in the account's
 * untrusted lane; the NIST cap counts both. Every value is read from the bound {@link LockoutProperties}. The attacker
 * and the owner sign in from their own source addresses, and each test uses its own accounts.
 */
class DeviceLaneTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String OWNER = "198.51.100.34";
    private static final String ATTACKER = "203.0.113.34";

    private static final String LOCKED = "Account locked.";
    private static final String LOCK_CLEARED = "Account lock cleared.";

    @Autowired
    private LockoutProperties lockout;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        // Onto a whole microsecond, the columns' precision, so a lock read back is exactly the rung from now.
        clock.advance(Duration.ofNanos(1_000 - clock.instant().getNano() % 1_000));
    }

    private int status(String source, Cookie device, Account account, String password) throws Exception {
        return DeviceCookies.login(mockMvc, source, device, account.username(), password).getResponse().getStatus();
    }

    /** {@code times} wrong passwords for {@code account} from {@code source}, presenting {@code device}. */
    private void fail(String source, Cookie device, Account account, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            assertThat(status(source, device, account, Accounts.WRONG_PASSWORD)).isEqualTo(401);
        }
    }

    private PasswordLockoutState state(Account account) {
        return accounts.lockoutState(account);
    }

    private DeviceLockState device(Cookie cookie) {
        return DeviceCookies.state(jdbc, cookie);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static List<Map<String, Object>> rowsFor(AuditCapture audit, String message, Account account) {
        return audit.withMessage(message).stream()
                .filter(row -> account.id().toString().equals(row.get("user.id")))
                .toList();
    }

    @Test
    @Proves({"T-LCK-024", "T-LCK-004"})
    void anUntrustedLaneLockRefusesEveryUntrustedClientButLetsTheOwnersTrustedDeviceIn() throws Exception {
        Account owner = accounts.user();
        Cookie trusted = DeviceCookies.earn(mockMvc, OWNER, owner);

        try (AuditCapture audit = AuditCapture.start()) {
            fail(ATTACKER, null, owner, lockout.threshold());
            assertThat(state(owner).lockedAt(clock.instant())).as("the untrusted lane is locked").isTrue();
            assertThat(rowsFor(audit, LOCKED, owner)).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("event.reason", "THRESHOLD_REACHED"));

            assertThat(status(ATTACKER, null, owner, owner.password())).as("an untrusted correct password")
                    .isEqualTo(401);
            assertThat(status(OWNER, null, owner, owner.password())).as("the owner without the cookie")
                    .isEqualTo(401);
            assertThat(status(OWNER, trusted, owner, owner.password())).as("the owner's trusted device")
                    .isEqualTo(200);
        }
    }

    @Test
    @Proves({"T-LCK-025", "T-LCK-004", "T-LCK-002"})
    void aTrustedDeviceLocksInItsOwnLaneOnTheLadderAndItsLockLiftsOnItsOwn() throws Exception {
        Account owner = accounts.user();
        Cookie laptop = DeviceCookies.earn(mockMvc, OWNER, owner);
        Cookie phone = DeviceCookies.earn(mockMvc, OWNER, owner);

        try (AuditCapture audit = AuditCapture.start()) {
            fail(OWNER, laptop, owner, lockout.threshold());
            DeviceLockState locked = device(laptop);
            assertThat(locked.lockedUntil()).as("the laptop locks for the first rung")
                    .isEqualTo(now().plus(lockout.ladder().rungs().getFirst()));
            assertThat(device(phone)).as("the phone's lane is untouched").isEqualTo(DeviceLockState.CLEAR);
            assertThat(state(owner)).as("the untrusted lane is untouched; the cap counts every lane")
                    .isEqualTo(new PasswordLockoutState(0, null, null, lockout.threshold(), null));
            assertThat(rowsFor(audit, LOCKED, owner)).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("event.reason", "TRUSTED_DEVICE_THRESHOLD_REACHED"));

            assertThat(status(OWNER, laptop, owner, owner.password())).as("the locked laptop's correct password")
                    .isEqualTo(401);
            assertThat(device(laptop)).as("attempts during a lock move no counter").isEqualTo(locked);
            assertThat(status(OWNER, phone, owner, owner.password())).as("the phone").isEqualTo(200);

            clock.advance(Duration.between(clock.instant(), locked.lockedUntil()).minusSeconds(1));
            assertThat(status(OWNER, laptop, owner, owner.password())).as("a second early").isEqualTo(401);
            clock.advance(Duration.ofSeconds(1));
            assertThat(status(OWNER, laptop, owner, owner.password())).as("lifted").isEqualTo(200);
            assertThat(device(laptop)).isEqualTo(DeviceLockState.CLEAR);
            assertThat(rowsFor(audit, LOCK_CLEARED, owner)).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("event.reason", "TRUSTED_DEVICE_AUTO_LIFT"));
        }
    }

    @Test
    @Proves("T-LCK-026")
    void aCookiePresentedWithAnotherUsernameIsUntrustedAndCountsInThatAccountsUntrustedLane() throws Exception {
        Account alice = accounts.user();
        Account bob = accounts.user();
        Cookie alices = DeviceCookies.earn(mockMvc, OWNER, alice);

        fail(ATTACKER, alices, bob, lockout.threshold());

        assertThat(device(alices)).as("alice's device lane is untouched").isEqualTo(DeviceLockState.CLEAR);
        assertThat(state(alice).consecutiveFailuresSinceSuccess()).isZero();
        assertThat(state(bob).lockedAt(clock.instant())).as("bob's untrusted lane locked").isTrue();
        assertThat(status(ATTACKER, alices, bob, bob.password())).as("bob's correct password with alice's cookie")
                .isEqualTo(401);
        assertThat(status(OWNER, alices, alice, alice.password())).as("the cookie still trusts alice").isEqualTo(200);
    }

    @Test
    @Proves("T-LCK-027")
    void aTamperedExpiredOrRevokedCookieIsUntrusted() throws Exception {
        Account owner = accounts.user();
        Cookie trusted = DeviceCookies.earn(mockMvc, OWNER, owner);
        fail(ATTACKER, null, owner, lockout.threshold());
        assertThat(state(owner).lockedAt(clock.instant())).isTrue();

        String value = trusted.getValue();
        char last = value.charAt(value.length() - 2);
        Cookie tampered = new Cookie(DeviceCookies.NAME, value.substring(0, value.length() - 2)
                + (last == 'A' ? 'B' : 'A') + value.charAt(value.length() - 1));
        Cookie forged = new Cookie(DeviceCookies.NAME, value.split("\\.")[0] + "." + "A".repeat(43));
        Cookie garbage = new Cookie(DeviceCookies.NAME, "not-a-device-cookie");
        for (Cookie untrusted : List.of(tampered, forged, garbage)) {
            assertThat(status(OWNER, untrusted, owner, owner.password())).as(untrusted.getValue()).isEqualTo(401);
        }
        assertThat(status(OWNER, trusted, owner, owner.password())).as("the real cookie").isEqualTo(200);

        // The trusted success left the untrusted lock in force, so each cookie below is tried against it.
        jdbc.update("UPDATE trusted_devices SET expires_at = ? WHERE id = ?", Timestamp.from(clock.instant()),
                DeviceCookies.idOf(trusted));
        assertThat(status(OWNER, trusted, owner, owner.password())).as("expired").isEqualTo(401);

        jdbc.update("DELETE FROM trusted_devices WHERE id = ?", DeviceCookies.idOf(trusted));
        assertThat(status(OWNER, trusted, owner, owner.password())).as("revoked").isEqualTo(401);
    }

    @Test
    @Proves("T-LCK-028")
    void aTrustedSuccessResetsTheCapCounterTheUntrustedCounterAndItsOwnButNotAnUntrustedLock() throws Exception {
        Account owner = accounts.user();
        Cookie trusted = DeviceCookies.earn(mockMvc, OWNER, owner);
        fail(ATTACKER, null, owner, lockout.threshold() - 1);
        fail(OWNER, trusted, owner, 2);
        assertThat(state(owner).consecutiveFailuresSinceSuccess()).as("the cap counts both lanes")
                .isEqualTo(lockout.threshold() + 1);
        assertThat(state(owner).failedLoginAttempts()).isEqualTo(lockout.threshold() - 1);
        assertThat(device(trusted).failedLoginAttempts()).isEqualTo(2);

        assertThat(status(OWNER, trusted, owner, owner.password())).isEqualTo(200);

        assertThat(state(owner)).isEqualTo(new PasswordLockoutState(0, null, null, 0, null));
        assertThat(device(trusted)).isEqualTo(DeviceLockState.CLEAR);

        fail(ATTACKER, null, owner, lockout.threshold());
        Instant lockedUntil = state(owner).lockedUntil();
        assertThat(status(OWNER, trusted, owner, owner.password())).isEqualTo(200);
        assertThat(state(owner)).as("the untrusted lock stays until it lifts")
                .isEqualTo(new PasswordLockoutState(0, null, lockedUntil, 0, null));
        assertThat(status(ATTACKER, null, owner, owner.password())).isEqualTo(401);
    }

    @Test
    @Proves("T-LCK-029")
    void theCapCountsEveryLaneAndDisablesThemAll() throws Exception {
        Account owner = accounts.user();
        Cookie trusted = DeviceCookies.earn(mockMvc, OWNER, owner);
        jdbc.update("UPDATE users SET consecutive_failures_since_success = ? WHERE id = ?",
                lockout.nist().cap() - 2, owner.id());

        fail(ATTACKER, null, owner, 1);
        assertThat(state(owner).consecutiveFailuresSinceSuccess()).isEqualTo(lockout.nist().cap() - 1);
        fail(OWNER, trusted, owner, 1);

        assertThat(state(owner).passwordDisabled()).as("the device lane's failure reached the cap").isTrue();
        assertThat(status(OWNER, trusted, owner, owner.password())).as("the trusted device").isEqualTo(401);
        assertThat(status(OWNER, null, owner, owner.password())).as("an untrusted client").isEqualTo(401);
    }

    @Test
    @Proves("T-LCK-031")
    void aCorrectPasswordEarnsADeviceCookieBoundToItsAccountAndNoFailureSetsOne() throws Exception {
        Account owner = accounts.user();
        MvcResult wrong = DeviceCookies.login(mockMvc, OWNER, null, owner.username(), Accounts.WRONG_PASSWORD);
        assertThat(wrong.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(header -> header.startsWith(DeviceCookies.NAME + "="));

        MvcResult right = DeviceCookies.login(mockMvc, OWNER, null, owner.username(), owner.password());
        String header = right.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith(DeviceCookies.NAME + "=")).findFirst().orElseThrow();
        assertThat(header).contains("Path=/", "HttpOnly", "SameSite=Strict",
                "Max-Age=" + lockout.device().ttl().toSeconds()).doesNotContain("Domain");
        Cookie cookie = right.getResponse().getCookie(DeviceCookies.NAME);
        Map<String, Object> row = jdbc.queryForMap("SELECT user_id, created_at, expires_at FROM trusted_devices"
                + " WHERE id = ?", DeviceCookies.idOf(cookie));
        assertThat(row.get("USER_ID")).isEqualTo(owner.id());

        MvcResult again = DeviceCookies.login(mockMvc, OWNER, cookie, owner.username(), owner.password());
        assertThat(again.getResponse().getCookie(DeviceCookies.NAME)).as("a trusted sign-in earns no second one")
                .isNull();
        MvcResult trustedWrong = DeviceCookies.login(mockMvc, OWNER, cookie, owner.username(),
                Accounts.WRONG_PASSWORD);
        assertThat(trustedWrong.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .as("a failure never sets or clears the cookie").isEmpty();
        assertThat(DeviceCookies.count(jdbc, owner.id())).isOne();
    }

    /**
     * The uniform refusal (ADR-033) holds across the device axis: an unknown username, a wrong password and a locked
     * lane, each with no cookie, the account's own cookie, or another account's, answer one status, one body with only
     * the trace id varying, and one set of headers. A failure never sets a cookie of either kind.
     */
    @Test
    @Proves("T-AUTH-019")
    void theRefusalIsIdenticalWhetherOrNotTheAccountExistsOrACookieIsPresent() throws Exception {
        Account owner = accounts.user();
        Account other = accounts.user();
        Account locked = accounts.user();
        Cookie owners = DeviceCookies.earn(mockMvc, OWNER, owner);
        Cookie others = DeviceCookies.earn(mockMvc, OWNER, other);
        Cookie lockeds = DeviceCookies.earn(mockMvc, OWNER, locked);
        fail(OWNER, lockeds, locked, lockout.threshold());
        String unknown = Accounts.unknownUsername();

        List<MvcResult> refusals = new ArrayList<>();
        for (Cookie cookie : Arrays.asList(null, owners, others)) {
            refusals.add(DeviceCookies.login(mockMvc, ATTACKER, cookie, unknown, Accounts.WRONG_PASSWORD));
            refusals.add(DeviceCookies.login(mockMvc, ATTACKER, cookie, owner.username(), Accounts.WRONG_PASSWORD));
        }
        refusals.add(DeviceCookies.login(mockMvc, ATTACKER, lockeds, locked.username(), locked.password()));

        MvcResult first = refusals.getFirst();
        for (MvcResult refusal : refusals) {
            assertThat(refusal.getResponse().getStatus()).isEqualTo(401);
            assertThat(bodyWithoutTraceId(refusal)).isEqualTo(bodyWithoutTraceId(first));
            assertThat(headers(refusal)).isEqualTo(headers(first));
            assertThat(refusal.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        }
    }

    private static String bodyWithoutTraceId(MvcResult result) throws Exception {
        ObjectNode body = (ObjectNode) JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        body.remove("traceId");
        return body.toString();
    }

    private static Map<String, List<String>> headers(MvcResult result) {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : result.getResponse().getHeaderNames()) {
            headers.put(name, Collections.unmodifiableList(result.getResponse().getHeaders(name)));
        }
        return headers;
    }
}
