package com.example.helloauth.service;

import com.example.helloauth.settings.AppProperties;
import com.example.helloauth.service.exception.AuthExceptions.TooManyRequestsException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Per-IP throttling of authentication traffic.
 *
 * <p>This exists because account lockout alone is exploitable: if the only brake were keyed to the
 * account, an attacker could lock a legitimate user out of their own account by failing that user's
 * password a handful of times. So the two mechanisms are independent by design, not by accident —
 * this one counts attempts per source address across every account.
 *
 * <p><strong>Limitation, stated plainly:</strong> the window lives in this process's heap. One
 * instance, one correct count. Two instances, two independent counts and an effective limit of
 * {@code maxFailures × instances}. That is acceptable for a reference application and wrong for a
 * real deployment, which needs a shared store such as Redis or throttling at the ingress.
 */
@Service
public class IpThrottleService {

    private final Map<String, Deque<Instant>> attemptsByIp = new ConcurrentHashMap<>();
    private final AppProperties properties;
    private final Clock clock;

    public IpThrottleService(AppProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Throws if this address has already used up its allowance for the current window. */
    public void assertNotThrottled(String clientIp) {
        if (countWithinWindow(clientIp) >= properties.throttle().maxFailures()) {
            throw new TooManyRequestsException();
        }
    }

    public void recordAttempt(String clientIp) {
        Deque<Instant> attempts = attemptsByIp.computeIfAbsent(clientIp, key -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(clock.instant());
        }
    }

    private int countWithinWindow(String clientIp) {
        Deque<Instant> attempts = attemptsByIp.get(clientIp);
        if (attempts == null) {
            return 0;
        }
        synchronized (attempts) {
            prune(attempts);
            return attempts.size();
        }
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = clock.instant().minus(properties.throttle().window());
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
    }

    /**
     * Drops addresses whose attempts have all aged out. Pruning already happens on access, but
     * without this an address seen once and never again would keep its entry forever, which over a
     * long uptime is a slow memory leak rather than a bounded cache.
     */
    @Scheduled(fixedDelayString = "PT5M")
    void evictIdleEntries() {
        attemptsByIp.forEach(
                (ip, attempts) -> {
                    synchronized (attempts) {
                        prune(attempts);
                        if (attempts.isEmpty()) {
                            attemptsByIp.remove(ip, attempts);
                        }
                    }
                });
    }

    /** Forgets every recorded attempt. Used by tests, and useful operationally after an incident. */
    public void clear() {
        attemptsByIp.clear();
    }
}
