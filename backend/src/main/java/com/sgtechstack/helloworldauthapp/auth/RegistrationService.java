package com.sgtechstack.helloworldauthapp.auth;

import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public RegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    /**
     * Registers a new account. Never logs the submitted password, on
     * either the success or failure path.
     *
     * @throws WeakPasswordException    if the password fails the strength policy
     * @throws DuplicateAccountException if the username or email is already taken
     */
    @Transactional
    public User register(RegistrationRequest request) {
        if (!passwordPolicy.isSatisfiedBy(request.password())) {
            throw new WeakPasswordException(
                    "Password must be at least " + PasswordPolicy.MIN_LENGTH + " characters long");
        }

        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new DuplicateAccountException("Username is already taken");
        }

        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateAccountException("Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(request.username(), request.email(), passwordHash, Role.USER, true);
        User saved = userRepository.save(user);

        log.info("Registered new account username={} role={}", saved.getUsername(), saved.getRole());

        return saved;
    }
}
