package com.assessment.securedhelloworld.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Holds the configured account-lockout policy (Story 3) as a
 * {@link LockoutPolicy}. The lockout state transitions themselves live
 * on {@code User} (see its "Lockout ownership" javadoc); this class only
 * reads {@code app.security.lockout.*} once at startup and hands the
 * resulting policy to callers such as {@code LoginService}.
 *
 * <p>IP-based rate limiting/throttling is intentionally not implemented
 * here — it is expected to be handled by a WAF or equivalent edge/CDN
 * layer in front of this application; see {@code /ARCHITECTURE.md}'s
 * "Known architectural limitations".
 */
@Component
public class LoginAttemptService {

    private final LockoutPolicy lockoutPolicy;

    public LoginAttemptService(
            @Value("${app.security.lockout.max-attempts}") int maxAccountAttempts,
            @Value("${app.security.lockout.duration-minutes}") long lockoutDurationMinutes) {
        this.lockoutPolicy = new LockoutPolicy(maxAccountAttempts, Duration.ofMinutes(lockoutDurationMinutes));
    }

    public LockoutPolicy policy() {
        return lockoutPolicy;
    }
}
