package hello.desk.security;

import hello.desk.config.AppProperties;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class IpThrottle {

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final AppProperties properties;

    public IpThrottle(AppProperties properties) {
        this.properties = properties;
    }

    public boolean isBlocked(String ip) {
        return recentFailures(ip) >= properties.security().ipMaxAttempts();
    }

    public void recordFailure(String ip) {
        Deque<Instant> deque = failures.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (deque) {
            Instant now = Instant.now();
            evict(deque, now);
            deque.addLast(now);
        }
    }

    public void reset() {
        failures.clear();
    }

    private int recentFailures(String ip) {
        Deque<Instant> deque = failures.get(ip);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            evict(deque, Instant.now());
            return deque.size();
        }
    }

    private void evict(Deque<Instant> deque, Instant now) {
        Instant cutoff = now.minus(properties.security().ipWindow());
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.removeFirst();
        }
    }
}
