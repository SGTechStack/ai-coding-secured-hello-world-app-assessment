package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Verifies credentials for login. Emits a single generic failure for every
 * unsuccessful case (unknown user, wrong password, disabled, locked) so account
 * existence cannot be inferred. Tracks failed_login_attempts and resets it on
 * success. The lockout TRIGGER (setting locked_until after N failures) is added
 * in ticket 07 via {@link LoginAttemptPolicy}; this slice increments the counter
 * (persisted in its own transaction) and rejects already-locked accounts.
 */
@Service
public class LoginService {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final FailedLoginRecorder failedLoginRecorder;
    private final IpThrottlingService ipThrottling;

    public LoginService(UserRepository users, PasswordEncoder passwordEncoder,
                        FailedLoginRecorder failedLoginRecorder,
                        IpThrottlingService ipThrottling) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.failedLoginRecorder = failedLoginRecorder;
        this.ipThrottling = ipThrottling;
    }

    public User authenticate(String username, String rawPassword, String clientIp) {
        // IP-level throttle is checked BEFORE credential verification and is
        // independent of per-account lockout: it blunts spraying from one source
        // (throws 429) before any single account can be locked out by that source.
        ipThrottling.checkAllowed(clientIp);

        Optional<User> maybe = users.findByUsername(username);
        if (maybe.isEmpty()) {
            ipThrottling.recordFailure(clientIp);
            audit.info("login failure username={} reason=unknown", username);
            throw new AuthenticationFailedException();
        }
        User user = maybe.get();

        if (isLocked(user)) {
            audit.info("login failure username={} reason=locked", username);
            throw new AuthenticationFailedException();
        }
        if (!user.isEnabled()) {
            audit.info("login failure username={} reason=disabled", username);
            throw new AuthenticationFailedException();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            // Persisted in a separate committed transaction so it survives the throw.
            failedLoginRecorder.recordFailure(username);
            ipThrottling.recordFailure(clientIp);
            throw new AuthenticationFailedException();
        }

        // Success: reset the counter and any lock. A single repository save is
        // its own transaction, and nothing throws after this point.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.save(user);
        audit.info("login success username={}", username);
        return user;
    }

    private boolean isLocked(User user) {
        return user.getLockedUntil() != null && Instant.now().isBefore(user.getLockedUntil());
    }
}
