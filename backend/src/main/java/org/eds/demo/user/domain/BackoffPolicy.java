package org.eds.demo.user.domain;

import java.time.Duration;

/**
 * How sign-in failures turn into a delay: from the {@code threshold}th consecutive failure the
 * delay starts at {@code baseDelay} and doubles per further failure up to {@code maxDelay}.
 */
public record BackoffPolicy(int threshold, Duration baseDelay, Duration maxDelay) {}
