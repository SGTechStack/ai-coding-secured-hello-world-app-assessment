package com.sgtechstack.helloauth.auth;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sgtechstack.helloauth.audit.AuditEvent;
import com.sgtechstack.helloauth.audit.AuditLog;
import com.sgtechstack.helloauth.user.Identifiers;
import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;
import com.sgtechstack.helloauth.user.UserRepository;

@Service
public class RegistrationService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	private final AuditLog audit;

	RegistrationService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock, AuditLog audit) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.audit = audit;
	}

	/**
	 * Creates an enabled USER account. The password must already satisfy the password policy.
	 * @throws RegistrationConflictException if the username or email is taken
	 */
	@Transactional
	public User register(String username, String email, String password, String clientIp) {
		String normalizedUsername = Identifiers.normalizeUsername(username);
		String normalizedEmail = Identifiers.normalizeEmail(email);

		Map<String, String> conflicts = new LinkedHashMap<>();
		if (this.users.existsByUsername(normalizedUsername)) {
			conflicts.put("username", "is already taken");
		}
		if (this.users.existsByEmail(normalizedEmail)) {
			conflicts.put("email", "is already registered");
		}
		if (!conflicts.isEmpty()) {
			throw new RegistrationConflictException(conflicts);
		}

		User user = User.create(normalizedUsername, normalizedEmail, this.passwordEncoder.encode(password), Role.USER,
				this.clock.instant());
		try {
			this.users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Lost a race with a concurrent registration; the unique constraints decided.
			throw new RegistrationConflictException(Map.of());
		}
		this.audit.event(AuditEvent.USER_REGISTERED).target(normalizedUsername).ip(clientIp).log();
		return user;
	}

}
