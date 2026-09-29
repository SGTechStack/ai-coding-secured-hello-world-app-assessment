package com.sgtechstack.helloauth.admin;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.sgtechstack.helloauth.audit.AuditLog;
import com.sgtechstack.helloauth.support.IntegrationTest;
import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;
import com.sgtechstack.helloauth.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

/**
 * Story 12: an admin is seeded from configuration when no enabled admin exists, and only then.
 */
class AdminBootstrapIntegrationTests extends IntegrationTest {

	// From application-test.yml
	private static final String CONFIGURED_USERNAME = "rootadmin";

	private static final String CONFIGURED_PASSWORD = "Bootstrap-Admin-Pass-1";

	@Autowired
	private AdminBootstrap adminBootstrap;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	void seedsTheConfiguredAdminWithABcryptHash() {
		User admin = this.users.findByUsername(CONFIGURED_USERNAME).orElseThrow();

		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(admin.isEnabled()).isTrue();
		assertThat(admin.getPasswordHash()).startsWith("$2a$").isNotEqualTo(CONFIGURED_PASSWORD);
		assertThat(this.passwordEncoder.matches(CONFIGURED_PASSWORD, admin.getPasswordHash())).isTrue();
		assertThat(newClient().login(CONFIGURED_USERNAME, CONFIGURED_PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void doesNotSeedAgainWhenAnAdminExists() throws Exception {
		long usersBefore = this.users.count();

		this.adminBootstrap.run(null);

		assertThat(this.users.count()).isEqualTo(usersBefore);
		assertThat(this.users.findByUsername(CONFIGURED_USERNAME)).isPresent();
	}

	/** Rolled back afterwards, so other tests still see their admins. */
	@Test
	@Transactional
	void disabledAdminsDoNotCountSoANewAdminIsSeeded() {
		disableEveryAdmin();
		AdminBootstrap bootstrap = new AdminBootstrap(this.users, this.passwordEncoder,
				new AdminBootstrapProperties("recovery_admin", "recovery@example.com", "Recovery-Admin-Pass-1"),
				this.transactionTemplate, this.clock, new AuditLog());

		bootstrap.run(null);

		User seeded = this.users.findByUsername("recovery_admin").orElseThrow();
		assertThat(seeded.getRole()).isEqualTo(Role.ADMIN);
		assertThat(seeded.isEnabled()).isTrue();
		assertThat(this.passwordEncoder.matches("Recovery-Admin-Pass-1", seeded.getPasswordHash())).isTrue();
	}

	@Test
	@Transactional
	void neverTakesOverTheConfiguredAccountEvenIfItIsADisabledAdmin() {
		disableEveryAdmin();
		long usersBefore = this.users.count();

		assertThatIllegalStateException().isThrownBy(() -> this.adminBootstrap.run(null))
			.withMessageContaining("APP_ADMIN_USERNAME");

		assertThat(this.users.findByUsername(CONFIGURED_USERNAME).orElseThrow().isEnabled()).isFalse();
		assertThat(this.users.count()).isEqualTo(usersBefore);
	}

	@Test
	void refusesToStartWithoutAUsablePasswordWhenNoAdminExists() {
		UserRepository emptyStore = mock(UserRepository.class);
		given(emptyStore.existsByRoleAndEnabledTrue(Role.ADMIN)).willReturn(false);

		for (String password : new String[] { null, "", "too-short" }) {
			AdminBootstrap bootstrap = new AdminBootstrap(emptyStore, this.passwordEncoder,
					new AdminBootstrapProperties("admin", "admin@example.com", password), mock(TransactionTemplate.class),
					Clock.systemUTC(), new AuditLog());

			assertThatIllegalStateException().isThrownBy(() -> bootstrap.run(null))
				.withMessageContaining("app.admin.password");
		}
		verify(emptyStore, never()).save(any());
	}

	private void disableEveryAdmin() {
		this.users.findAll().stream().filter(user -> user.getRole() == Role.ADMIN).forEach(user -> user.setEnabled(false));
		this.users.flush();
	}

}
