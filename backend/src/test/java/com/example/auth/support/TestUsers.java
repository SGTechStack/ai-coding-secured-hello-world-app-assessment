package com.example.auth.support;

import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Creates users directly in the (shared, JVM-wide) H2 database for integration tests. */
public final class TestUsers {

    private TestUsers() {}

    /** A short random suffix, so tests that can't clean up after themselves never collide. */
    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static User create(
            UserRepository repository, PasswordEncoder encoder, String username, String password, Role role) {
        User user = new User(username, username + "@example.com", encoder.encode(password), "Test");
        user.setRole(role);
        return repository.save(user);
    }

    /** Finds {@code username} or creates it, then clears lockout state and restores password, role and status. */
    public static User reset(
            UserRepository repository, PasswordEncoder encoder, String username, String password, Role role) {
        User user = repository.findByUsername(username)
                .orElseGet(() -> new User(username, username + "@example.com", encoder.encode(password), "Test"));
        if (!encoder.matches(password, user.getPassword())) {
            user.setPassword(encoder.encode(password));
        }
        user.setRole(role);
        user.setEnabled(true);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        return repository.save(user);
    }
}
