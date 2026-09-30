package sg.example.helloauth.loginprotection;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param lockout when repeated failed logins lock an Account
 * @param throttle how many attempts a client may make before it is Throttled
 */
@ConfigurationProperties("app.login-protection")
@Validated
record LoginProtectionProperties(@Valid @DefaultValue Lockout lockout, @Valid @DefaultValue Throttle throttle) {

    /**
     * @param threshold consecutive failed logins that lock the Account
     * @param duration how long the lock lasts before it lifts by itself
     */
    record Lockout(@Positive @DefaultValue("5") int threshold, @DefaultValue("20m") Duration duration) {
    }

    /**
     * @param failedLoginsPerIp failed logins from one client IP, whatever the usernames (ADR-0003)
     * @param loginsPerUsername login attempts for one submitted username, from anywhere
     * @param registrationsPerIp registration attempts from one client IP
     * @param passwordResetRequestsPerIp reset-link requests from one client IP
     * @param passwordResetRequestsPerEmail reset-link requests for one submitted email, from
     *        anywhere, so reset emails can't flood an inbox
     * @param passwordResetConfirmsPerIp attempts to redeem a Password reset token from one client IP
     */
    record Throttle(
            @Valid @DefaultValue({"30", "10m"}) Limit failedLoginsPerIp,
            @Valid @DefaultValue({"10", "1m"}) Limit loginsPerUsername,
            @Valid @DefaultValue({"10", "1h"}) Limit registrationsPerIp,
            @Valid @DefaultValue({"10", "1h"}) Limit passwordResetRequestsPerIp,
            @Valid @DefaultValue({"3", "1h"}) Limit passwordResetRequestsPerEmail,
            @Valid @DefaultValue({"10", "1h"}) Limit passwordResetConfirmsPerIp) {
    }

    /** At most {@code attempts} per {@code period}, earned back steadily over the period. */
    record Limit(@Positive int attempts, Duration period) {
    }
}
