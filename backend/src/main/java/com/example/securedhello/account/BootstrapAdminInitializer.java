package com.example.securedhello.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.config.BootstrapAdminProperties;
import com.example.securedhello.credential.CredentialPolicy;
import com.example.securedhello.credential.PasswordPolicyException;

/**
 * Creates the Bootstrap Admin at startup when no Account holds the Admin role, enabled or not, so
 * a first deployment reaches the admin screens without editing the database. The password goes
 * through the Credential policy and BCrypt like any other, and the Account must change it at first
 * login.
 * <p>
 * Outside {@code dev}, a missing username, password or email fails startup, whether or not an Admin
 * exists, so production can never run on a known default. In {@code dev}, missing values fall back
 * to {@code admin} / {@code password} / {@code admin@localhost} with a WARN, and the fallback
 * password skips the policy. Startup also fails when the username or email already belongs to an
 * Account, so the wrong person is never promoted.
 */
@Component
class BootstrapAdminInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

	private static final String PREFIX = "app.bootstrap-admin.";

	private static final String DEV_USERNAME = "admin";

	private static final String DEV_PASSWORD = "password";

	private static final String DEV_EMAIL = "admin@localhost";

	/** The registration username format. */
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9]{3,32}");

	private final BootstrapAdminProperties properties;

	private final Environment environment;

	private final CredentialPolicy credentialPolicy;

	private final AccountRepository accounts;

	private final PasswordHistoryRepository passwordHistory;

	private final AuditLog auditLog;

	private final Clock clock;

	BootstrapAdminInitializer(BootstrapAdminProperties properties, Environment environment,
			CredentialPolicy credentialPolicy, AccountRepository accounts, PasswordHistoryRepository passwordHistory,
			AuditLog auditLog, Clock clock) {
		this.properties = properties;
		this.environment = environment;
		this.credentialPolicy = credentialPolicy;
		this.accounts = accounts;
		this.passwordHistory = passwordHistory;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
		String username = valueOrFallback("username", properties.username(), DEV_USERNAME, dev);
		String password = valueOrFallback("password", properties.password(), DEV_PASSWORD, dev);
		String email = valueOrFallback("email", properties.email(), DEV_EMAIL, dev);
		if (accounts.existsByRole(Role.ADMIN)) {
			return;
		}
		boolean fallbackPassword = isMissing(properties.password());
		if (!fallbackPassword) {
			checkPolicy(password);
		}
		if (!USERNAME.matcher(username).matches()) {
			throw new IllegalStateException(PREFIX + "username must be 3 to 32 letters or digits");
		}
		String normalisedUsername = username.toLowerCase(Locale.ROOT);
		String normalisedEmail = email.toLowerCase(Locale.ROOT);
		if (accounts.existsByUsername(normalisedUsername)) {
			throw new IllegalStateException(PREFIX + "username already belongs to an Account; configure another one");
		}
		if (accounts.existsByEmail(normalisedEmail)) {
			throw new IllegalStateException(PREFIX + "email already belongs to an Account; configure another one");
		}
		Instant now = clock.instant();
		Account admin = accounts.saveAndFlush(
				Account.bootstrapAdmin(normalisedUsername, normalisedEmail, credentialPolicy.hash(password), now));
		passwordHistory.save(new PasswordHistoryEntry(admin.getId(), admin.getPasswordHash(), now));
		if (dev && (fallbackPassword || isMissing(properties.username()) || isMissing(properties.email()))) {
			log.atWarn()
				.addKeyValue("user.id", admin.getId().toString())
				.log("Bootstrap Admin created from the dev fallback credentials. "
						+ "Never use them outside local development.");
		}
		auditLog.record(AuditEvent.success(AuditAction.USER_PROVISIONING)
			.reason("bootstrap_admin")
			.userId(admin.getId()));
	}

	private static String valueOrFallback(String name, String value, String fallback, boolean dev) {
		if (!isMissing(value)) {
			return value;
		}
		if (dev) {
			return fallback;
		}
		throw new IllegalStateException(PREFIX + name + " must be injected from the secrets manager");
	}

	private void checkPolicy(String password) {
		try {
			credentialPolicy.check(password);
		}
		catch (PasswordPolicyException ex) {
			// Only the broken rules' names, never the password.
			throw new IllegalStateException(
					PREFIX + "password breaks the Credential policy: " + String.join(", ", ex.violations()));
		}
	}

	private static boolean isMissing(String value) {
		return value == null || value.isBlank();
	}

}
