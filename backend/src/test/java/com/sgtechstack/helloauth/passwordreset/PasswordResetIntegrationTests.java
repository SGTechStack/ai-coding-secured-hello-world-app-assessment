package com.sgtechstack.helloauth.passwordreset;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import com.sgtechstack.helloauth.support.ApiClient;
import com.sgtechstack.helloauth.support.IntegrationTest;
import com.sgtechstack.helloauth.support.RecordingEmailService.SentEmail;
import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Stories 6 and 7: password reset request and confirmation.
 */
class PasswordResetIntegrationTests extends IntegrationTest {

	private static final String NEW_PASSWORD = "Brand-New-Passphrase-42";

	@Autowired
	private PasswordResetTokenRepository tokens;

	@Test
	void responseIsIdenticalWhetherOrNotTheEmailIsRegistered() {
		User user = createUser(Role.USER);

		MockHttpServletResponse registered = requestReset(user.getEmail());
		MockHttpServletResponse unregistered = requestReset("nobody-" + uniqueUsername() + "@example.com");

		assertThat(registered.getStatus()).isEqualTo(202);
		assertThat(unregistered.getStatus()).isEqualTo(202);
		assertThat(ApiClient.body(unregistered)).isEqualTo(ApiClient.body(registered));
		awaitResetEmail(user);
	}

	@Test
	void onlyAHashOfTheTokenIsStoredWithAShortExpiry() {
		User user = createUser(Role.USER);
		requestReset(user.getEmail());

		SentEmail email = awaitResetEmail(user);

		assertThat(email.link().toString()).startsWith("http://localhost:3000/reset-password#token=");
		assertThat(this.tokens.findByTokenHash(email.token())).isEmpty();
		PasswordResetToken stored = this.tokens.findByTokenHash(ResetTokens.hash(email.token())).orElseThrow();
		assertThat(stored.getExpiresAt()).isEqualTo(this.clock.instant().plus(Duration.ofMinutes(30)));
		assertThat(stored.getUsedAt()).isNull();
	}

	@Test
	void validTokenSetsNewPasswordAndCanOnlyBeUsedOnce() {
		User user = createUser(Role.USER);
		requestReset(user.getEmail());
		String token = awaitResetEmail(user).token();

		assertThat(confirm(token, NEW_PASSWORD).getStatus()).isEqualTo(204);

		assertProblem(newClient().login(user.getUsername(), PASSWORD), 401, "INVALID_CREDENTIALS");
		assertThat(newClient().login(user.getUsername(), NEW_PASSWORD).getStatus()).isEqualTo(200);
		assertProblem(confirm(token, "Yet-Another-Passphrase-7"), 400, "INVALID_RESET_TOKEN");
		assertThat(newClient().login(user.getUsername(), NEW_PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void expiredTokenIsRejectedAndPasswordIsUnchanged() {
		User user = createUser(Role.USER);
		requestReset(user.getEmail());
		String token = awaitResetEmail(user).token();

		this.clock.advance(Duration.ofMinutes(30));

		assertProblem(confirm(token, NEW_PASSWORD), 400, "INVALID_RESET_TOKEN");
		assertThat(newClient().login(user.getUsername(), PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void resetInvalidatesEveryExistingSessionOfTheUser() {
		User user = createUser(Role.USER);
		ApiClient laptop = loggedIn(user);
		ApiClient phone = loggedIn(user);
		ApiClient bystander = loggedIn(createUser(Role.USER));
		requestReset(user.getEmail());

		confirm(awaitResetEmail(user).token(), NEW_PASSWORD);

		assertProblem(laptop.get("/api/hello"), 401, "UNAUTHENTICATED");
		assertProblem(phone.get("/api/hello"), 401, "UNAUTHENTICATED");
		assertThat(bystander.get("/api/hello").getStatus()).isEqualTo(200);
	}

	@Test
	void requestingAgainInvalidatesThePreviousLink() {
		User user = createUser(Role.USER);
		requestReset(user.getEmail());
		String first = awaitResetEmail(user).token();
		requestReset(user.getEmail());
		await().atMost(Duration.ofSeconds(5)).until(() -> this.emails.sentTo(user.getEmail()).size() == 2);
		String second = this.emails.lastSentTo(user.getEmail()).orElseThrow().token();

		assertProblem(confirm(first, NEW_PASSWORD), 400, "INVALID_RESET_TOKEN");
		assertThat(confirm(second, NEW_PASSWORD).getStatus()).isEqualTo(204);
	}

	@Test
	void weakNewPasswordIsRejectedAndTheTokenStaysUsable() {
		User user = createUser(Role.USER);
		requestReset(user.getEmail());
		String token = awaitResetEmail(user).token();

		MockHttpServletResponse weak = confirm(token, "short");

		assertProblem(weak, 400, "VALIDATION_FAILED");
		assertThat(ApiClient.body(weak)).contains("newPassword").doesNotContain("short\"");
		assertThat(confirm(token, NEW_PASSWORD).getStatus()).isEqualTo(204);
	}

	@Test
	void resetClearsAnActiveLockout() {
		User user = createUser(Role.USER);
		for (int i = 0; i < 5; i++) {
			newClient().login(user.getUsername(), "wrong-password-" + i);
		}
		assertThat(reload(user).getLockedUntil()).isNotNull();
		requestReset(user.getEmail());

		confirm(awaitResetEmail(user).token(), NEW_PASSWORD);

		assertThat(newClient().login(user.getUsername(), NEW_PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void resetRequestsAreThrottledPerIp() {
		ApiClient client = newClient();
		for (int i = 0; i < 5; i++) {
			assertThat(client.post("/api/auth/password-reset/request", Map.of("email", "flood@example.com"))
				.getStatus()).isEqualTo(202);
		}

		assertProblem(client.post("/api/auth/password-reset/request", Map.of("email", "flood@example.com")), 429,
				"TOO_MANY_REQUESTS");
	}

	private MockHttpServletResponse requestReset(String email) {
		return newClient().post("/api/auth/password-reset/request", Map.of("email", email));
	}

	private MockHttpServletResponse confirm(String token, String newPassword) {
		return newClient().post("/api/auth/password-reset/confirm",
				Map.of("token", token, "newPassword", newPassword));
	}

	private SentEmail awaitResetEmail(User user) {
		await().atMost(Duration.ofSeconds(5)).until(() -> this.emails.lastSentTo(user.getEmail()).isPresent());
		return this.emails.lastSentTo(user.getEmail()).orElseThrow();
	}

}
