package com.example.helloauth.user;

import com.example.helloauth.config.AppProperties;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Account lifecycle operations. Registration enforces the ratified policy:
 * password length only (no complexity rules), unique username + email,
 * every self-registered account is {@code USER} and enabled.
 */
@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AppProperties properties;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder,
            Clock clock, AppProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.properties = properties;
    }

    public User register(String username, String email, String rawPassword) {
        int minLength = properties.getPasswordMinLength();
        if (rawPassword == null || rawPassword.length() < minLength) {
            throw new RegistrationException.PasswordTooShort(minLength);
        }
        if (users.existsByUsername(username)) {
            throw new RegistrationException.DuplicateUsername(username);
        }
        if (users.existsByEmail(email)) {
            throw new RegistrationException.DuplicateEmail(email);
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        // BCrypt — plaintext is never stored. (Audit logging arrives with the
        // hardening ticket; nothing here may ever log rawPassword.)
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setCreatedAt(clock.instant());
        return users.save(user);
    }
}
