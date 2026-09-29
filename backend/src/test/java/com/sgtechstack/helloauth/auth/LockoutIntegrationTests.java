package com.sgtechstack.helloauth.auth;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.sgtechstack.helloauth.support.IntegrationTest;
import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 3: account lockout after 5 consecutive failures within 15 minutes, for 15 minutes.
 */
class LockoutIntegrationTests extends IntegrationTest {

	private static final int MAX_ATTEMPTS = 5;

	@Test
	void fifthConsecutiveFailureLocksTheAccount() {
		User user = createUser(Role.USER);
		var client = newClient();

		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			assertProblem(client.login(user.getUsername(), "wrong-password-" + i), 401, "INVALID_CREDENTIALS");
		}

		User locked = reload(user);
		assertThat(locked.getFailedLoginAttempts()).isEqualTo(MAX_ATTEMPTS);
		assertThat(locked.getLockedUntil()).isEqualTo(this.clock.instant().plus(Duration.ofMinutes(15)));
		assertProblem(client.login(user.getUsername(), PASSWORD), 401, "INVALID_CREDENTIALS");
	}

	@Test
	void fourFailuresDoNotLock() {
		User user = createUser(Role.USER);
		var client = newClient();
		for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
			client.login(user.getUsername(), "wrong-password-" + i);
		}

		assertThat(reload(user).getLockedUntil()).isNull();
		assertThat(client.login(user.getUsername(), PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void correctPasswordAfterCooldownSucceedsAndResetsTheCounter() {
		User user = createUser(Role.USER);
		var client = newClient();
		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			client.login(user.getUsername(), "wrong-password-" + i);
		}
		this.clock.advance(Duration.ofMinutes(14));
		assertProblem(client.login(user.getUsername(), PASSWORD), 401, "INVALID_CREDENTIALS");

		this.clock.advance(Duration.ofMinutes(1));

		assertThat(client.login(user.getUsername(), PASSWORD).getStatus()).isEqualTo(200);
		User unlocked = reload(user);
		assertThat(unlocked.getFailedLoginAttempts()).isZero();
		assertThat(unlocked.getLockedUntil()).isNull();
	}

	@Test
	void failuresOlderThanTheWindowAreForgotten() {
		User user = createUser(Role.USER);
		var client = newClient();
		for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
			client.login(user.getUsername(), "wrong-password-" + i);
		}

		this.clock.advance(Duration.ofMinutes(16));
		client.login(user.getUsername(), "wrong-password-again");

		User after = reload(user);
		assertThat(after.getFailedLoginAttempts()).isEqualTo(1);
		assertThat(after.getLockedUntil()).isNull();
	}

	@Test
	void parallelGuessesCannotExceedTheThreshold() throws Exception {
		User user = createUser(Role.USER);
		int guesses = 20;
		List<Future<Integer>> results = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
			for (int i = 0; i < guesses; i++) {
				String guess = "parallel-guess-" + i;
				// A distinct IP per guess keeps the IP throttle out of the way.
				results.add(pool.submit(() -> newClient().login(user.getUsername(), guess).getStatus()));
			}
			for (Future<Integer> result : results) {
				assertThat(result.get()).isEqualTo(401);
			}
		}

		User after = reload(user);
		assertThat(after.getFailedLoginAttempts()).isEqualTo(MAX_ATTEMPTS);
		assertThat(after.getLockedUntil()).isNotNull();
	}

}
