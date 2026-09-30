package com.assessment.hello.service;

import com.assessment.hello.config.AppProperties;
import com.assessment.hello.domain.User;
import com.assessment.hello.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Mutates a user's lockout counters in their own committed transactions. These run
 * with REQUIRES_NEW so a failed-attempt increment survives even though the enclosing
 * login flow ultimately throws (and would otherwise roll the change back).
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;
    private final AppProperties appProperties;

    public LoginAttemptService(UserRepository userRepository, AppProperties appProperties) {
        this.userRepository = userRepository;
        this.appProperties = appProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        int max = appProperties.getSecurity().getLockout().getMaxAttempts();
        if (attempts >= max) {
            int cooldown = appProperties.getSecurity().getLockout().getCooldownMinutes();
            user.setLockedUntil(Instant.now().plusSeconds(cooldown * 60L));
            log.warn("Account lockout triggered username={} lockedUntil={}",
                    user.getUsername(), user.getLockedUntil());
        }
        userRepository.save(user);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }
}
