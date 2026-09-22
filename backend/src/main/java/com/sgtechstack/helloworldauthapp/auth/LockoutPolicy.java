package com.sgtechstack.helloworldauthapp.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Account-lockout policy: after {@link #MAX_FAILED_ATTEMPTS} consecutive
 * failed logins, the account is locked for {@link #LOCKOUT_DURATION}.
 */
@Component
public class LockoutPolicy {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    public boolean shouldLock(int failedAttempts) {
        return failedAttempts >= MAX_FAILED_ATTEMPTS;
    }
}
