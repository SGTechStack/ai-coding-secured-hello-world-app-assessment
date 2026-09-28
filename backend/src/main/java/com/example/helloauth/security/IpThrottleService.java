package com.example.helloauth.security;

import com.example.helloauth.config.AppProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP failed-login throttling, independent of any single account's lockout state (IM8 as-4).
 * An attacker failing many usernames from one IP is throttled without being able to lock out a
 * legitimate user from a different source.
 */
@Service
public class IpThrottleService {

    private final AppProperties props;
    private final Map<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();

    public IpThrottleService(AppProperties props) {
        this.props = props;
    }

    public synchronized boolean isThrottled(String ip) {
        Deque<Instant> failures = failuresByIp.get(ip);
        if (failures == null) {
            return false;
        }
        prune(failures);
        return failures.size() >= props.getThrottle().getMaxPerIp();
    }

    public synchronized void recordFailure(String ip) {
        Deque<Instant> failures = failuresByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        failures.addLast(Instant.now());
        prune(failures);
    }

    public synchronized void reset(String ip) {
        failuresByIp.remove(ip);
    }

    private void prune(Deque<Instant> failures) {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(props.getThrottle().getWindowMinutes()));
        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.pollFirst();
        }
    }
}
