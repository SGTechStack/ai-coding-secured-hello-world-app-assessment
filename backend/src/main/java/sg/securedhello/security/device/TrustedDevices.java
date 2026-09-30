package sg.securedhello.security.device;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.security.source.DeviceClaim;
import sg.securedhello.user.TrustedDevice;
import sg.securedhello.user.TrustedDeviceRepository;
import sg.securedhello.user.UserAccountRepository;

/**
 * Device cookies (ADR-075): reading the claim a sign-in's cookie makes, and issuing a new trusted device after a
 * correct password. A device cookie never authenticates anything: it only picks the lockout lane a password attempt
 * counts in, and a correct password is always required.
 *
 * <p>The cookie is {@code __Host-DEVICE} ({@code DEVICE} under {@code dev}, whose plain HTTP cannot carry
 * {@code Secure}, as the session cookie does; ADR-058), {@code HttpOnly}, {@code SameSite=Strict}, host-only,
 * {@code Path=/} (the {@code __Host-} prefix requires it) and {@code Max-Age} of the configured lifetime. It is set only
 * on a successful sign-in, and never set or cleared on a failure (ADR-033).
 */
public final class TrustedDevices {

    /** The cookie's name outside {@code dev}. */
    public static final String SECURE_COOKIE = "__Host-DEVICE";

    /** The cookie's name under {@code dev}, whose plain HTTP cannot carry the prefix's {@code Secure}. */
    public static final String DEV_COOKIE = "DEVICE";

    /**
     * The most devices one account keeps. Each needs a correct password, so only the password's holder can add them;
     * the bound only keeps that holder from growing the table without limit. The oldest go first.
     */
    static final int MAXIMUM_PER_ACCOUNT = 10;

    private final DeviceCookieCodec codec;
    private final TrustedDeviceRepository devices;
    private final UserAccountRepository accounts;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration ttl;
    private final boolean secure;

    TrustedDevices(DeviceCookieCodec codec, TrustedDeviceRepository devices, UserAccountRepository accounts,
            TransactionTemplate transactions, Clock clock, Duration ttl, boolean secure) {
        this.codec = codec;
        this.devices = devices;
        this.accounts = accounts;
        this.transactions = transactions;
        this.clock = clock;
        this.ttl = ttl;
        this.secure = secure;
    }

    /** The device cookie's name in this deployment. */
    public String cookieName() {
        return secure ? SECURE_COOKIE : DEV_COOKIE;
    }

    /**
     * The claim of the first device cookie on {@code request} whose HMAC verifies and whose device is still trusted:
     * not revoked, not expired. It reads the cookie alone, never the submitted username, so the lookup costs the same
     * whether or not that account exists (ADR-001).
     */
    public Optional<DeviceClaim> claim(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return Stream.of(cookies)
                .filter(cookie -> cookieName().equals(cookie.getName()))
                .flatMap(cookie -> codec.verify(cookie.getValue()).stream())
                .flatMap(id -> devices.findById(id).stream())
                .filter(device -> device.trustedAt(now))
                .findFirst()
                .map(device -> new DeviceClaim(device.getId(), device.getUserId(), device.getExpiresAt(),
                        device.getLockState().lockedUntil()));
    }

    /**
     * Trusts a new device for {@code userId}, which has just entered its correct password, and sets its cookie on
     * {@code response}. Under the account's row lock, like every other device write: the account's expired devices go,
     * and its oldest while it holds {@value #MAXIMUM_PER_ACCOUNT} or more.
     */
    public void issue(UUID userId, HttpServletResponse response) {
        // The columns are TIMESTAMP(6): stored at their precision, so a read-back compares equal.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID deviceId = transactions.execute(status -> {
            accounts.findForUpdateById(userId).orElseThrow();
            List<TrustedDevice> held = devices.findByUserIdOrderByCreatedAtDesc(userId);
            List<TrustedDevice> kept = held.stream().filter(device -> device.trustedAt(now)).toList();
            devices.deleteAll(held.stream().filter(device -> !device.trustedAt(now)).toList());
            devices.deleteAll(kept.stream().skip(MAXIMUM_PER_ACCOUNT - 1L).toList());
            return devices.save(new TrustedDevice(userId, now, now.plus(ttl))).getId();
        });
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(cookieName(), codec.sign(deviceId))
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(ttl)
                .build()
                .toString());
    }
}
