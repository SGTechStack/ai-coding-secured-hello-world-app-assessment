package com.example.auth.auth;

import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (request.password() == null || request.password().length() < MINIMUM_PASSWORD_LENGTH) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least " + MINIMUM_PASSWORD_LENGTH + " characters long");
        }

        // Normalized here, at the service boundary, so uniqueness checks and
        // the persisted value always agree regardless of the case the client
        // submitted.
        String normalizedEmail = request.email().toLowerCase(Locale.ROOT);

        // Case-insensitive on purpose: "Alice" and "alice" would otherwise
        // both be free to register, which is confusing (which one logs in
        // with which case?) even though login itself stays case-sensitive.
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username is already taken");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }

        User user = new User(
                request.username(), normalizedEmail, passwordEncoder.encode(request.password()), request.firstName());
        return userRepository.save(user);
    }
}
