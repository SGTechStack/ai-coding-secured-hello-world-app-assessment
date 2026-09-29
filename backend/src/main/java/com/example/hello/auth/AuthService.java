package com.example.hello.auth;

import com.example.hello.shared.ApiException;
import com.example.hello.shared.AuditLog;
import com.example.hello.user.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final PasswordPolicy policy;
    private final AuditLog audit;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, PasswordPolicy policy, AuditLog audit, Clock clock) {
        this.users = users; this.passwords = passwords; this.policy = policy; this.audit = audit; this.clock = clock;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public UserView register(AuthRequests.Register request) {
        policy.validate(request.password());
        if (users.existsByUsername(request.username())) throw new ApiException(HttpStatus.CONFLICT, "Username is already registered.");
        if (users.existsByEmail(request.email())) throw new ApiException(HttpStatus.CONFLICT, "Email is already registered.");
        return UserView.from(users.saveAndFlush(new UserAccount(request.username(), request.email(),
                passwords.encode(request.password()), Role.USER, clock.instant())));
    }

    // Return failures instead of throwing: failed-attempt updates must commit.
    @Transactional
    public Optional<UserAccount> authenticate(AuthRequests.Login request) {
        Optional<UserAccount> account = users.lockByUsername(request.username());
        boolean validSize = request.password().getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean matches = passwords.matches(validSize ? request.password() : "invalid-password",
                account.map(UserAccount::getPasswordHash).orElse(dummyHash)) && validSize;
        if (account.isEmpty()) {
            audit.record("login", request.username(), request.username(), "failure");
            return Optional.empty();
        }
        UserAccount user = account.get();
        if (!user.isEnabled() || user.isLocked(clock.instant())) {
            audit.record("login", user.getUsername(), user.getUsername(), "failure");
            return Optional.empty();
        }
        if (!matches) {
            if (user.recordFailure(clock.instant())) audit.record("lockout", user.getUsername(), user.getUsername(), "locked");
            audit.record("login", user.getUsername(), user.getUsername(), "failure");
            return Optional.empty();
        }
        user.clearFailures();
        audit.record("login", user.getUsername(), user.getUsername(), "success");
        return Optional.of(user);
    }
}
