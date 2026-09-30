package sg.securedhello.testsupport;

import org.springframework.http.HttpHeaders;

import sg.securedhello.security.device.TrustedDevices;

/**
 * The session cookie among a response's {@code Set-Cookie} headers. A successful sign-in from a browser with no device
 * cookie also sets one (ADR-075), ahead of the session cookie, which Spring Session writes as the response commits; so
 * a test that reads the session cookie off a sign-in picks it by name, not by position.
 */
public final class SessionCookies {

    private SessionCookies() {
    }

    /** The session cookie's {@code name=value}, the first {@code Set-Cookie} that is not the device cookie. */
    public static String pair(HttpHeaders headers) {
        return headers.getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .map(header -> header.split(";", 2)[0])
                .filter(pair -> !isDevice(pair))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no session Set-Cookie in " + headers));
    }

    /** The session cookie's value. */
    public static String value(HttpHeaders headers) {
        return pair(headers).split("=", 2)[1];
    }

    /** Whether a {@code name=value} pair is the device cookie, under either profile's name. */
    public static boolean isDevice(String pair) {
        return pair.startsWith(TrustedDevices.DEV_COOKIE + "=") || pair.startsWith(TrustedDevices.SECURE_COOKIE + "=");
    }
}
