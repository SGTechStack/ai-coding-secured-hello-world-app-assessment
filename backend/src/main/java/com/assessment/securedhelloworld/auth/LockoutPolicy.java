package com.assessment.securedhelloworld.auth;

import java.time.Duration;

/**
 * Configured account-lockout policy: how many consecutive failed
 * attempts trigger a lockout, and how long that lockout lasts. Passed
 * into {@code User.recordFailedAttempt} rather than threading the two
 * primitives separately, so a future policy change (e.g. an escalating
 * duration) only grows this record, not every call site's signature.
 */
public record LockoutPolicy(int maxAttempts, Duration lockoutDuration) {
}
