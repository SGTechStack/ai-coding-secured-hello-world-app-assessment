package com.assessment.hello.service;

import com.assessment.hello.config.AppProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks failed login attempts per source IP, independent of any single account's
 * lockout state. This prevents an attacker from locking out a legitimate user by
 * failing their password from one source, while still blunting spray attacks that
 * cycle usernames from one IP.
 */
@Service
public class IpThrottleService {

    private final AppProperties appProperties;
    private final ConcurrentHashMap<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();

    public IpThrottleService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public boolean isThrottled(String ip) {
        Attempts a = attemptsByIp.get(ip);
        if (a == null) {
            return false;
        }
        if (a.windowStart.plusSeconds(windowSeconds()).isBefore(Instant.now())) {
            // Window elapsed; reset.
            attemptsByIp.remove(ip);
            return false;
        }
        return a.count >= appProperties.getSecurity().getIpThrottle().getMaxAttempts();
    }

    public void recordFailure(String ip) {
        Instant now = Instant.now();
        attemptsByIp.compute(ip, (key, existing) -> {
            if (existing == null
                    || existing.windowStart.plusSeconds(windowSeconds()).isBefore(now)) {
                return new Attempts(now, 1);
            }
            existing.count++;
            return existing;
        });
    }

    public void reset(String ip) {
        attemptsByIp.remove(ip);
    }

    private long windowSeconds() {
        return appProperties.getSecurity().getIpThrottle().getWindowMinutes() * 60L;
    }

    private static final class Attempts {
        private final Instant windowStart;
        private int count;

        private Attempts(Instant windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
