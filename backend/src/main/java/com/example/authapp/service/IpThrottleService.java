package com.example.authapp.service;

import com.example.authapp.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory sliding-window counter of failed logins per client IP. Independent of per-account
 * lockout. Single-node only; use a shared store (e.g. Redis) if the app is scaled out.
 */
@Service
public class IpThrottleService {

    private static final int MAX_TRACKED_IPS = 10_000;

    private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;

    public IpThrottleService(AppProperties props) {
        this.maxFailures = props.ipThrottle().maxFailures();
        this.window = Duration.ofMinutes(props.ipThrottle().windowMinutes());
    }

    public boolean isBlocked(String ip) {
        Deque<Instant> q = failures.get(ip);
        if (q == null) {
            return false;
        }
        synchronized (q) {
            prune(q);
            return q.size() >= maxFailures;
        }
    }

    public void recordFailure(String ip) {
        if (failures.size() > MAX_TRACKED_IPS) {
            failures.values().removeIf(q -> {
                synchronized (q) {
                    prune(q);
                    return q.isEmpty();
                }
            });
        }
        Deque<Instant> q = failures.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(Instant.now());
        }
    }

    private void prune(Deque<Instant> q) {
        Instant cutoff = Instant.now().minus(window);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
            q.pollFirst();
        }
    }
}
