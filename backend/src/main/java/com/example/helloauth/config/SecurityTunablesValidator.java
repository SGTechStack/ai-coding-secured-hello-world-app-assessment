package com.example.helloauth.config;

import org.springframework.stereotype.Component;

/**
 * Startup fail-fast for the config relationship that only holds via shipped
 * defaults (reviewer decision, {@code docs/agents/reviewer-decisions.md}):
 * the anti-DoS AC — one source IP can never lock an account — holds because
 * the IP-throttle gate runs first and {@code ip-throttle.max-failures (4) <
 * lockout.max-failures (5)}. An operator override that raises the throttle
 * at or above the lockout threshold would silently reintroduce single-IP
 * account lockout, so the misconfiguration refuses to boot instead.
 *
 * <p>Validation lives in the constructor: bean creation fails during
 * context refresh — before any {@code ApplicationRunner} (seeder, etc.) —
 * and {@code SpringApplication.run} exits non-zero with the property names
 * in the failure message.
 */
@Component
public class SecurityTunablesValidator {

    public SecurityTunablesValidator(AppProperties properties) {
        int throttleMax = properties.getIpThrottle().getMaxFailures();
        int lockoutMax = properties.getLockout().getMaxFailures();
        if (throttleMax >= lockoutMax) {
            throw new IllegalStateException(
                "app.ip-throttle.max-failures (" + throttleMax
                    + ") must be less than app.lockout.max-failures ("
                    + lockoutMax + ") — otherwise a single source IP can "
                    + "record enough failures to lock an account, breaking "
                    + "the anti-DoS guarantee.");
        }
    }
}
