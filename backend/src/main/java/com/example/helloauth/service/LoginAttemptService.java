package com.example.helloauth.service;

import com.example.helloauth.config.AppProperties;
import com.example.helloauth.domain.User;
import com.example.helloauth.repo.UserRepository;
import com.example.helloauth.security.AuditLogger;
import com.example.helloauth.security.IpThrottleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Persists login-attempt outcomes in their own transaction (REQUIRES_NEW) so that a failed-attempt
 * increment / lockout is committed even though the surrounding authentication call ultimately
 * throws (which would otherwise roll back the counter).
 */
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final IpThrottleService ipThrottle;
    private final AuditLogger audit;
    private final AppProperties props;

    public LoginAttemptService(UserRepository userRepository, IpThrottleService ipThrottle,
                               AuditLogger audit, AppProperties props) {
        this.userRepository = userRepository;
        this.ipThrottle = ipThrottle;
        this.audit = audit;
        this.props = props;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId, String ip, String reason, String username) {
        ipThrottle.recordFailure(ip);
        audit.loginFailure(username, ip, reason);

        if (userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= props.getLockout().getMaxAttempts()) {
                user.setLockedUntil(Instant.now().plus(
                        props.getLockout().getCooldownMinutes(), ChronoUnit.MINUTES));
                audit.lockoutTriggered(username, ip);
            }
            userRepository.save(user);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID userId, String ip, String username) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
        });
        ipThrottle.reset(ip);
        audit.loginSuccess(username, ip);
    }
}
