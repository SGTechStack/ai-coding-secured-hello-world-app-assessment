package com.example.auth.security.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;

/**
 * Every rate limiter in the app, built from {@code app.security.rate-limit.*} (see
 * application.properties and ADR-0005). Kept in one place so tests can reset all of them at once
 * and so the limits are reviewable side by side.
 */
@Component
public class RateLimiters {

    /** One limiter's configuration. {@code block} of zero means "blocked until the window ends". */
    public record Limit(int maxHits, Duration window, @DefaultValue("0s") Duration block) {}

    @ConfigurationProperties(prefix = "app.security.rate-limit")
    public record Properties(
            @DefaultValue("10000") int maxKeys,
            Limit loginIp,
            Limit loginIpUsername,
            Limit loginUsername,
            Limit registerIp,
            Limit resetRequestIp,
            Limit resetRequestEmail,
            Limit resetConfirmIp) {}

    private final FixedWindowRateLimiter loginIp;
    private final FixedWindowRateLimiter loginIpUsername;
    private final FixedWindowRateLimiter loginUsername;
    private final FixedWindowRateLimiter registerIp;
    private final FixedWindowRateLimiter resetRequestIp;
    private final FixedWindowRateLimiter resetRequestEmail;
    private final FixedWindowRateLimiter resetConfirmIp;

    public RateLimiters(Properties properties) {
        int maxKeys = properties.maxKeys();
        this.loginIp = build("login_ip", properties.loginIp(), maxKeys);
        this.loginIpUsername = build("login_ip_username", properties.loginIpUsername(), maxKeys);
        this.loginUsername = build("login_username", properties.loginUsername(), maxKeys);
        this.registerIp = build("register_ip", properties.registerIp(), maxKeys);
        this.resetRequestIp = build("reset_request_ip", properties.resetRequestIp(), maxKeys);
        this.resetRequestEmail = build("reset_request_email", properties.resetRequestEmail(), maxKeys);
        this.resetConfirmIp = build("reset_confirm_ip", properties.resetConfirmIp(), maxKeys);
    }

    private static FixedWindowRateLimiter build(String name, Limit limit, int maxKeys) {
        return new FixedWindowRateLimiter(name, limit.maxHits(), limit.window(), limit.block(), maxKeys);
    }

    /** Login failures per source IP (password-spray defence across many usernames). */
    public FixedWindowRateLimiter loginIp() {
        return loginIp;
    }

    /** Login failures per (IP, username): trips before account lockout so one source can't lock a user out. */
    public FixedWindowRateLimiter loginIpUsername() {
        return loginIpUsername;
    }

    /** All login attempts per username (App-Standards: 10 per account per minute). */
    public FixedWindowRateLimiter loginUsername() {
        return loginUsername;
    }

    public FixedWindowRateLimiter registerIp() {
        return registerIp;
    }

    public FixedWindowRateLimiter resetRequestIp() {
        return resetRequestIp;
    }

    public FixedWindowRateLimiter resetRequestEmail() {
        return resetRequestEmail;
    }

    public FixedWindowRateLimiter resetConfirmIp() {
        return resetConfirmIp;
    }

    public static String ipUsernameKey(String ip, String username) {
        return ip + "|" + username;
    }

    /** Test-only. */
    public void resetAll() {
        List.of(loginIp, loginIpUsername, loginUsername, registerIp, resetRequestIp, resetRequestEmail, resetConfirmIp)
                .forEach(FixedWindowRateLimiter::reset);
    }
}
