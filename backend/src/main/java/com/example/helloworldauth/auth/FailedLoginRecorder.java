package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a failed login attempt in its OWN committed transaction, so the
 * incremented counter survives even though the caller then throws
 * {@link AuthenticationFailedException} to reject the request. Without this the
 * throw would roll back the increment and lockout could never accumulate.
 */
@Service
public class FailedLoginRecorder {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final UserRepository users;
    private final LoginAttemptPolicy attemptPolicy;

    public FailedLoginRecorder(UserRepository users, LoginAttemptPolicy attemptPolicy) {
        this.users = users;
        this.attemptPolicy = attemptPolicy;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String username) {
        users.findByUsername(username).ifPresent(user -> {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            attemptPolicy.onFailure(user);
            users.save(user);
            audit.info("login failure username={} reason=bad_credentials attempts={}",
                username, user.getFailedLoginAttempts());
        });
    }
}
