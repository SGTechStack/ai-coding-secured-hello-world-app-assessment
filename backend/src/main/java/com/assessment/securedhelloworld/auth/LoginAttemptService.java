package com.assessment.securedhelloworld.auth;

import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-account failed-login counting/lockout (Story 3) and
 * per-IP throttling, independent of any single account's lockout state
 * (ticket 04 requirement). IP throttling state is in-memory, sufficient
 * for a single-instance dev-profile deployment.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;
    private final int maxAccountAttempts;
    private final Duration lockoutDuration;
    private final int maxIpAttempts;
    private final Duration ipThrottleWindow;

    private final Map<String, IpAttemptWindow> ipAttempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            UserRepository userRepository,
            @Value("${app.security.lockout.max-attempts}") int maxAccountAttempts,
            @Value("${app.security.lockout.duration-minutes}") long lockoutDurationMinutes,
            @Value("${app.security.ip-throttle.max-attempts}") int maxIpAttempts,
            @Value("${app.security.ip-throttle.window-minutes}") long ipThrottleWindowMinutes) {
        this.userRepository = userRepository;
        this.maxAccountAttempts = maxAccountAttempts;
        this.lockoutDuration = Duration.ofMinutes(lockoutDurationMinutes);
        this.maxIpAttempts = maxIpAttempts;
        this.ipThrottleWindow = Duration.ofMinutes(ipThrottleWindowMinutes);
    }

    public void recordFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= maxAccountAttempts) {
            user.setLockedUntil(Instant.now().plus(lockoutDuration));
            log.warn("Account locked username={} attempts={}", user.getUsername(), attempts);
        }

        userRepository.save(user);
    }

    public void recordSuccess(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    public void recordFailureForIp(String clientIp) {
        IpAttemptWindow window = ipAttempts.computeIfAbsent(clientIp, ip -> new IpAttemptWindow(Instant.now()));

        synchronized (window) {
            if (Instant.now().isAfter(window.windowStart.plus(ipThrottleWindow))) {
                window.windowStart = Instant.now();
                window.attempts = 0;
            }
            window.attempts++;
        }
    }

    public void assertIpNotThrottled(String clientIp) {
        IpAttemptWindow window = ipAttempts.get(clientIp);
        if (window == null) {
            return;
        }

        synchronized (window) {
            boolean windowExpired = Instant.now().isAfter(window.windowStart.plus(ipThrottleWindow));
            if (windowExpired) {
                window.windowStart = Instant.now();
                window.attempts = 0;
                return;
            }
            if (window.attempts >= maxIpAttempts) {
                log.warn("IP throttled ip={} attempts={}", clientIp, window.attempts);
                throw new AuthenticationFailedException();
            }
        }
    }

    private static final class IpAttemptWindow {
        private Instant windowStart;
        private int attempts;

        private IpAttemptWindow(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
