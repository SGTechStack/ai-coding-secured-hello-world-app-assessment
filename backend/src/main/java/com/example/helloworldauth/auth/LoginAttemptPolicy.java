package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Account-lockout policy (ticket 07). Called from
 * {@link FailedLoginRecorder#recordFailure(String)} AFTER the failed-attempt
 * counter has been incremented, inside that recorder's own committed
 * ({@code REQUIRES_NEW}) transaction — so setting {@code locked_until} here is
 * persisted even though the login flow then throws to reject the request.
 *
 * <p>When the counter reaches {@code app.security.lockout.max-attempts} the
 * account is locked for {@code app.security.lockout.cooldown-minutes} by setting
 * {@code locked_until}. {@link LoginService} rejects locked accounts and resets
 * the counter + lock on a successful login after the cooldown elapses.
 */
@Component
public class LoginAttemptPolicy {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final int maxAttempts;
    private final Duration cooldown;

    public LoginAttemptPolicy(
            @Value("${app.security.lockout.max-attempts:5}") int maxAttempts,
            @Value("${app.security.lockout.cooldown-minutes:15}") long cooldownMinutes) {
        this.maxAttempts = maxAttempts;
        this.cooldown = Duration.ofMinutes(cooldownMinutes);
    }

    /** Called after a failed attempt, with the counter already incremented. */
    public void onFailure(User user) {
        if (user.getFailedLoginAttempts() >= maxAttempts && user.getLockedUntil() == null) {
            user.setLockedUntil(Instant.now().plus(cooldown));
            audit.info("account locked username={} attempts={} until={}",
                user.getUsername(), user.getFailedLoginAttempts(), user.getLockedUntil());
        }
    }
}
