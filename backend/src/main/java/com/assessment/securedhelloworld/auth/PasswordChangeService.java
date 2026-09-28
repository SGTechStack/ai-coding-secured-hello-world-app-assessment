package com.assessment.securedhelloworld.auth;

import com.assessment.securedhelloworld.logging.LogSanitizer;
import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service password change for an authenticated user. On success,
 * clears {@link User#isForcePasswordChange()} so a forced change (e.g.
 * the bootstrap admin credential) only ever needs to happen once.
 */
@Service
public class PasswordChangeService {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordChangeService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(AppUserDetails principal, PasswordChangeRequest request) {
        User user = userRepository.findById(principal.getUser().getId())
                .orElseThrow(InvalidCurrentPasswordException::new);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setForcePasswordChange(false);
        userRepository.save(user);

        log.info("Password changed username={}", LogSanitizer.sanitize(user.getUsername()));
    }
}
