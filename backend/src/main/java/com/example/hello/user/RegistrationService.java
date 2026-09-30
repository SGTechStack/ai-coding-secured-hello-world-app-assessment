package com.example.hello.user;

import com.example.hello.common.AuditLogger;
import com.example.hello.common.ConflictException;
import java.time.Clock;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Story 1: self-service registration. */
@Service
public class RegistrationService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final AuditLogger audit;
  private final Clock clock;

  public RegistrationService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy,
      AuditLogger audit,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public UserSummary register(RegisterRequest request) {
    String username = request.username().trim();
    String email = request.email().trim().toLowerCase(Locale.ROOT);

    if (userRepository.existsByUsername(username)) {
      throw new ConflictException("username", "Username is already taken");
    }
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new ConflictException("email", "Email is already registered");
    }
    passwordPolicy.validate(request.password(), username);

    User user =
        new User(username, email, passwordEncoder.encode(request.password()), Role.USER, clock.instant());
    User saved = userRepository.save(user);
    audit.event("USER_REGISTERED", "username", username);
    return UserSummary.from(saved);
  }
}
