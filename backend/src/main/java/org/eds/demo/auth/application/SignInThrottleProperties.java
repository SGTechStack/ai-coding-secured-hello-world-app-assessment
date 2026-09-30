package org.eds.demo.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Sign-in throttling, bound under {@code app.sign-in}.
 *
 * <p>Per-Account backoff: from the {@code backoffThreshold}th consecutive failure on, each failure
 * refuses sign-in for {@code baseDelay} doubled once per failure past the threshold, up to {@code
 * maxDelay}. The Account is never locked permanently.
 *
 * <p>Per-IP throttle: once {@code ipMaxFailures} failures from one address fall within {@code
 * ipWindow}, that address is refused until the oldest one leaves the window. It is held in memory
 * and does not depend on any Account. The address is the servlet remote address, which reflects
 * {@code X-Forwarded-For} only when {@code server.forward-headers-strategy} is configured.
 *
 * @param backoffThreshold consecutive failures on one Account before a delay applies
 * @param baseDelay first delay
 * @param maxDelay upper bound for the doubled delay
 * @param ipMaxFailures failures allowed per address within the window
 * @param ipWindow sliding window for the per-address count
 */
@ConfigurationProperties(prefix = "app.sign-in")
public record SignInThrottleProperties(
    @DefaultValue("3") int backoffThreshold,
    @DefaultValue("1s") Duration baseDelay,
    @DefaultValue("15m") Duration maxDelay,
    @DefaultValue("10") int ipMaxFailures,
    @DefaultValue("15m") Duration ipWindow) {}
