package com.example.securedhello.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * The last-Admin rule under real concurrency (spec "Account administration service": "checked
 * under a pessimistic row lock in the same transaction, so two concurrent demotions can't both
 * succeed"). Two Admins each try to demote the other, at the same moment, when they are the only
 * two enabled Admins; exactly one must succeed.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountAdministrationServiceConcurrencyTest {

	@Autowired
	AccountAdministrationService administration;

	@Autowired
	AccountRepository accounts;

	@Autowired
	JdbcTemplate jdbc;

	private UUID adminA;

	private UUID adminB;

	@BeforeEach
	void twoEnabledAdmins() {
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM users");
		adminA = enabledAdmin("concurrentadmina");
		adminB = enabledAdmin("concurrentadminb");
	}

	@AfterEach
	void clearAuthentication() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void exactlyOneOfTwoConcurrentDemotionsOfTheLastTwoAdminsSucceeds() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Outcome> aDemotesB = pool.submit(demote(adminA, adminB, ready, go));
			Future<Outcome> bDemotesA = pool.submit(demote(adminB, adminA, ready, go));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			go.countDown();

			Outcome aDemotedB = aDemotesB.get(10, TimeUnit.SECONDS);
			Outcome bDemotedA = bDemotesA.get(10, TimeUnit.SECONDS);

			assertThat(List.of(aDemotedB, bDemotedA)).containsExactlyInAnyOrder(Outcome.SUCCEEDED, Outcome.REJECTED);
			UUID survivingAdmin = (aDemotedB == Outcome.SUCCEEDED) ? adminA : adminB;
			// A plain read (no lock, no transaction needed here) of the DB state both transactions left.
			assertThat(jdbc.queryForList("SELECT id FROM users WHERE role = 'ADMIN' AND enabled = true", String.class))
				.containsExactly(survivingAdmin.toString());
		}
		finally {
			pool.shutdownNow();
		}
	}

	/** Demotes {@code targetId} as {@code actingAdminId}, once both racing calls are ready to start. */
	private Callable<Outcome> demote(UUID actingAdminId, UUID targetId, CountDownLatch ready, CountDownLatch go) {
		return () -> {
			authenticateAs(actingAdminId);
			try {
				ready.countDown();
				go.await();
				administration.changeRole(actingAdminId, targetId, Role.USER);
				return Outcome.SUCCEEDED;
			}
			catch (LastAdminException ex) {
				return Outcome.REJECTED;
			}
			finally {
				SecurityContextHolder.clearContext();
			}
		};
	}

	private UUID enabledAdmin(String username) {
		Account account = Account.registered(username, username + "@test.example.com", "synthetic-hash", Instant.now());
		account.changeRole(Role.ADMIN);
		return accounts.save(account).getId();
	}

	private static void authenticateAs(UUID accountId) {
		SecurityContextHolder.getContext()
			.setAuthentication(new AccountPrincipal(accountId, "synthetic", Role.ADMIN).toAuthentication());
	}

	private enum Outcome {

		SUCCEEDED, REJECTED

	}

}
