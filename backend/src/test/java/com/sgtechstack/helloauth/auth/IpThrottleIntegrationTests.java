package com.sgtechstack.helloauth.auth;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import com.sgtechstack.helloauth.support.ApiClient;
import com.sgtechstack.helloauth.support.IntegrationTest;
import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 3: per-IP throttling across usernames, independent of account lockout.
 */
class IpThrottleIntegrationTests extends IntegrationTest {

	private static final int MAX_FAILURES_PER_IP = 20;

	@Test
	void ipIsThrottledAcrossUsernamesWithoutLockingAnyAccount() {
		String attackerIp = uniqueIp();
		ApiClient attacker = newClient(attackerIp);
		// 5 accounts x 4 failures: 20 failures from one IP, yet no single account reaches its lockout.
		List<User> sprayed = IntStream.range(0, 5).mapToObj(i -> createUser(Role.USER)).toList();
		for (User target : sprayed) {
			for (int i = 0; i < 4; i++) {
				assertProblem(attacker.login(target.getUsername(), "spray-" + i), 401, "INVALID_CREDENTIALS");
			}
		}
		User victim = createUser(Role.USER);

		MockHttpServletResponse throttled = attacker.login(victim.getUsername(), PASSWORD);

		assertProblem(throttled, 429, "TOO_MANY_REQUESTS");
		assertThat(Long.parseLong(throttled.getHeader(HttpHeaders.RETRY_AFTER))).isPositive();
		assertThat(sprayed).allSatisfy(user -> assertThat(reload(user).getLockedUntil()).isNull());
		assertThat(reload(victim).getFailedLoginAttempts()).isZero();
		assertThat(newClient().login(victim.getUsername(), PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void throttledIpCannotAddFailuresToAVictimAccount() {
		ApiClient attacker = newClient();
		for (int i = 0; i < MAX_FAILURES_PER_IP; i++) {
			attacker.login(uniqueUsername(), "spray-" + i);
		}
		User victim = createUser(Role.USER);

		for (int i = 0; i < 10; i++) {
			assertProblem(attacker.login(victim.getUsername(), "guess-" + i), 429, "TOO_MANY_REQUESTS");
		}

		assertThat(reload(victim).getFailedLoginAttempts()).isZero();
		assertThat(reload(victim).getLockedUntil()).isNull();
	}

	@Test
	void successfulLoginsDoNotUseUpTheIpBudget() {
		// e.g. many users behind one office NAT address
		String sharedIp = uniqueIp();
		User user = createUser(Role.USER);

		for (int i = 0; i < MAX_FAILURES_PER_IP + 5; i++) {
			assertThat(newClient(sharedIp).login(user.getUsername(), PASSWORD).getStatus()).isEqualTo(200);
		}
	}

}
