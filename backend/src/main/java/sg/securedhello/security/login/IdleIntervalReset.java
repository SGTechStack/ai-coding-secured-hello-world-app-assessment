package sg.securedhello.security.login;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

/**
 * Sets the signed-in session's idle interval back to W, which the anonymous pin had shortened (ADR-038). It sits
 * beside the {@code AUTH_INSTANT} stamp, not inside it, and touches nothing else, so running it again is harmless and
 * never re-stamps the auth instant (T-SES-034).
 */
final class IdleIntervalReset implements SessionAuthenticationStrategy {

    private final int seconds;

    IdleIntervalReset(Duration window) {
        this.seconds = Math.toIntExact(window.toSeconds());
    }

    @Override
    public void onAuthentication(Authentication authentication, HttpServletRequest request,
            HttpServletResponse response) {
        request.getSession().setMaxInactiveInterval(seconds);
    }
}
