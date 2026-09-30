package com.example.auth.auth;

import com.example.auth.audit.AuditLogger;
import com.example.auth.user.PasswordPolicy;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    /**
     * One message for both a taken username and a registered email (spec D6): the PRD requires a
     * clear conflict error, but naming which field collided would tell an attacker whether a given
     * email has an account. Paired with a per-IP registration rate limit.
     */
    public static final String CONFLICT_MESSAGE = "Username or email is already registered";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (!PasswordPolicy.isAcceptable(request.password())) {
            throw ApiException.validation(PasswordPolicy.MESSAGE);
        }

        // Normalized here, at the service boundary, so uniqueness checks and
        // the persisted value always agree regardless of the case the client
        // submitted.
        String normalizedEmail = request.email().toLowerCase(Locale.ROOT);

        // Case-insensitive on purpose: "Alice" and "alice" would otherwise
        // both be free to register, which is confusing (which one logs in
        // with which case?) even though login itself stays case-sensitive.
        if (userRepository.existsByUsernameIgnoreCase(request.username())
                || userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, CONFLICT_MESSAGE);
        }

        User user = new User(
                request.username(), normalizedEmail, passwordEncoder.encode(request.password()), request.firstName());
        User saved = userRepository.save(user);
        auditLogger.registered(saved.getPublicId());
        return saved;
    }
}
