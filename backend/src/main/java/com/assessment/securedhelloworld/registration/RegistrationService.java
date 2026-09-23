package com.assessment.securedhelloworld.registration;

import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
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

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegistrationRequest request) {
        // Deliberately never log request.getPassword() (or any derivative
        // of it) anywhere in this method, including in exception messages.
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateAccountException("Username is already registered");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateAccountException("Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        User user = new User(request.getUsername(), request.getEmail(), passwordHash);
        User saved = userRepository.save(user);

        log.info("Registered new user username={}", saved.getUsername());
        return saved;
    }
}
