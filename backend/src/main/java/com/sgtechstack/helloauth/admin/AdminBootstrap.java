package com.sgtechstack.helloauth.admin;

import java.time.Clock;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.sgtechstack.helloauth.audit.AuditEvent;
import com.sgtechstack.helloauth.audit.AuditLog;
import com.sgtechstack.helloauth.security.PasswordPolicy;
import com.sgtechstack.helloauth.user.Identifiers;
import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;
import com.sgtechstack.helloauth.user.UserRepository;

/**
 * Seeds an ADMIN from configuration when no enabled ADMIN exists. Startup fails, rather than
 * continuing without an admin or with a weak password, if the configuration is unusable.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private static final Pattern USERNAME = Pattern.compile(Identifiers.USERNAME_PATTERN);

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final AdminBootstrapProperties properties;

	private final TransactionTemplate transaction;

	private final Clock clock;

	private final AuditLog audit;

	AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, AdminBootstrapProperties properties,
			TransactionTemplate transaction, Clock clock, AuditLog audit) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
		this.transaction = transaction;
		this.clock = clock;
		this.audit = audit;
	}

	@Override
	public void run(ApplicationArguments args) {
		// Only an enabled admin can sign in, so a disabled one does not count: if every admin
		// has been disabled, the admin module is unreachable and a new admin is seeded.
		if (this.users.existsByRoleAndEnabledTrue(Role.ADMIN)) {
			log.info("An enabled ADMIN account exists; skipping admin bootstrap");
			return;
		}
		String username = validUsername();
		String email = validEmail();
		String password = validPassword();
		// Never take over an existing account, even a disabled admin: its password and email
		// are not the operator's to assume.
		if (this.users.existsByUsername(username) || this.users.existsByEmail(email)) {
			throw new IllegalStateException("No enabled ADMIN account exists, and app.admin.username or "
					+ "app.admin.email already belongs to an existing account. Set APP_ADMIN_USERNAME and "
					+ "APP_ADMIN_EMAIL to unused values to seed a new admin.");
		}
		try {
			this.transaction.executeWithoutResult(status -> this.users.save(User.create(username, email,
					this.passwordEncoder.encode(password), Role.ADMIN, this.clock.instant())));
		}
		catch (DataIntegrityViolationException ex) {
			// Another instance seeded concurrently; that is only acceptable if it created an admin.
			if (!this.users.existsByRoleAndEnabledTrue(Role.ADMIN)) {
				throw ex;
			}
			log.info("An ADMIN account was created concurrently; skipping admin bootstrap");
			return;
		}
		this.audit.event(AuditEvent.ADMIN_BOOTSTRAPPED).target(username).log();
	}

	private String validUsername() {
		String username = this.properties.username();
		if (!StringUtils.hasText(username) || !USERNAME.matcher(username).matches()) {
			throw new IllegalStateException(
					"No ADMIN account exists and app.admin.username is missing or not a valid username");
		}
		return Identifiers.normalizeUsername(username);
	}

	private String validEmail() {
		String email = this.properties.email();
		if (!StringUtils.hasText(email) || !EMAIL.matcher(email.strip()).matches()) {
			throw new IllegalStateException(
					"No ADMIN account exists and app.admin.email is missing or not a valid email address");
		}
		return Identifiers.normalizeEmail(email);
	}

	private String validPassword() {
		if (!PasswordPolicy.isAcceptable(this.properties.password())) {
			throw new IllegalStateException("No ADMIN account exists: set app.admin.password (env APP_ADMIN_PASSWORD); "
					+ "it " + PasswordPolicy.DESCRIPTION);
		}
		return this.properties.password();
	}

}
