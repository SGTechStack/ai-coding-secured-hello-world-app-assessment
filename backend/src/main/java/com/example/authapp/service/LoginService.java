package com.example.authapp.service;

import com.example.authapp.config.AppProperties;
import com.example.authapp.domain.User;
import com.example.authapp.domain.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final IpThrottleService ipThrottle;
    private final AuditLogger audit;
    private final int maxAttempts;
    private final Duration cooldown;
    /** Compared against for unknown usernames so response time doesn't reveal existence. */
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordEncoder encoder, IpThrottleService ipThrottle,
            AuditLogger audit, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.ipThrottle = ipThrottle;
        this.audit = audit;
        this.maxAttempts = props.lockout().maxAttempts();
        this.cooldown = Duration.ofMinutes(props.lockout().cooldownMinutes());
        this.dummyHash = encoder.encode("dummy-password-for-timing-equalisation");
    }

    /**
     * Returns the user on success, empty on any failure (callers must respond with one generic
     * error). Throws {@link TooManyAttemptsException} if the client IP is throttled; that check
     * happens before any account state is touched, so a throttled source can't grind an account
     * toward lockout.
     */
    @Transactional(noRollbackFor = TooManyAttemptsException.class)
    public Optional<User> authenticate(String username, String password, String ip) {
        if (ipThrottle.isBlocked(ip)) {
            audit.log("login_throttled", "ip", ip);
            throw new TooManyAttemptsException();
        }

        Optional<User> found = users.findByUsernameIgnoreCase(username);
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);
            ipThrottle.recordFailure(ip);
            audit.log("login_failure", "user", username, "ip", ip, "reason", "unknown_user");
            return Optional.empty();
        }

        User user = found.get();
        Instant now = Instant.now();
        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
        boolean locked = user.getLockedUntil() != null;
        boolean passwordOk = encoder.matches(password, user.getPasswordHash());

        if (!passwordOk) {
            ipThrottle.recordFailure(ip);
            if (locked) {
                audit.log("login_failure", "user", user.getUsername(), "ip", ip, "reason", "locked");
                return Optional.empty();
            }
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            audit.log("login_failure", "user", user.getUsername(), "ip", ip, "reason", "bad_password");
            if (attempts >= maxAttempts) {
                user.setLockedUntil(now.plus(cooldown));
                audit.log("account_locked", "user", user.getUsername(), "until", user.getLockedUntil());
            }
            return Optional.empty();
        }

        if (locked || !user.isEnabled()) {
            audit.log("login_failure", "user", user.getUsername(), "ip", ip,
                    "reason", locked ? "locked" : "disabled");
            return Optional.empty();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        audit.log("login_success", "user", user.getUsername(), "ip", ip);
        return Optional.of(user);
    }
}
