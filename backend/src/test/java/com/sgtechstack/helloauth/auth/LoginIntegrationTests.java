package com.sgtechstack.helloauth.auth;

import java.time.Duration;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

import com.sgtechstack.helloauth.support.ApiClient;
import com.sgtechstack.helloauth.support.IntegrationTest;
import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 2 (login) and Story 5 (protected greeting).
 */
class LoginIntegrationTests extends IntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void successfulLoginCreatesHardenedSessionAndResetsFailedAttempts() {
		User user = createUser(Role.USER);
		ApiClient client = newClient();
		client.login(user.getUsername(), "wrong-password-1");
		client.login(user.getUsername(), "wrong-password-2");
		assertThat(reload(user).getFailedLoginAttempts()).isEqualTo(2);

		MockHttpServletResponse response = client.login(user.getUsername(), PASSWORD);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(client.json(response).path("username").asString()).isEqualTo(user.getUsername());
		assertThat(client.json(response).path("role").asString()).isEqualTo("USER");
		assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
			.anySatisfy(cookie -> assertThat(cookie).startsWith(ApiClient.SESSION_COOKIE + "=")
				.contains("HttpOnly")
				.contains("Secure")
				.contains("SameSite=Strict"));
		assertThat(reload(user).getFailedLoginAttempts()).isZero();
		assertThat(ApiClient.body(client.get("/api/hello"))).isEqualTo("Hello, " + user.getUsername());
	}

	@Test
	void sessionsExpireAfterFifteenMinutesWithoutActivity() {
		User user = createUser(Role.USER);
		loggedIn(user);

		Integer maxInactiveSeconds = this.jdbc.queryForObject(
				"select max(MAX_INACTIVE_INTERVAL) from SPRING_SESSION where PRINCIPAL_NAME = ?", Integer.class,
				user.getUsername());

		assertThat(maxInactiveSeconds).isEqualTo(15 * 60);
	}

	@Test
	void usernameIsCaseInsensitive() {
		User user = createUser(Role.USER);

		assertThat(newClient().login(user.getUsername().toUpperCase(), PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void wrongPasswordAndUnknownUsernameAreIndistinguishable() {
		User user = createUser(Role.USER);

		MockHttpServletResponse wrongPassword = newClient().login(user.getUsername(), "not-the-password");
		MockHttpServletResponse unknownUser = newClient().login(uniqueUsername(), "not-the-password");

		assertProblem(wrongPassword, 401, "INVALID_CREDENTIALS");
		assertThat(ApiClient.body(unknownUser)).isEqualTo(ApiClient.body(wrongPassword));
		assertThat(unknownUser.getStatus()).isEqualTo(wrongPassword.getStatus());
		assertThat(reload(user).getFailedLoginAttempts()).isEqualTo(1);
	}

	@Test
	void lockedAccountRejectsCorrectPasswordWithTheSameGenericError() {
		User user = createUser(Role.USER);
		user.lockUntil(this.clock.instant().plus(Duration.ofMinutes(10)));
		this.users.save(user);

		MockHttpServletResponse locked = newClient().login(user.getUsername(), PASSWORD);
		MockHttpServletResponse wrongPassword = newClient().login(createUser(Role.USER).getUsername(), "nope-nope-nope");

		assertProblem(locked, 401, "INVALID_CREDENTIALS");
		assertThat(ApiClient.body(locked)).isEqualTo(ApiClient.body(wrongPassword));
	}

	@Test
	void disabledAccountCannotLogIn() {
		User user = createUser(Role.USER);
		user.setEnabled(false);
		this.users.save(user);

		assertProblem(newClient().login(user.getUsername(), PASSWORD), 401, "INVALID_CREDENTIALS");
	}

	@Test
	void loginChangesTheSessionIdToPreventFixation() {
		User user = createUser(Role.USER);
		ApiClient client = newClient();
		client.get("/api/auth/csrf");
		Cookie preLoginSession = client.sessionCookie();
		assertThat(preLoginSession).isNotNull();

		client.login(user.getUsername(), PASSWORD);

		assertThat(client.sessionCookie().getValue()).isNotEqualTo(preLoginSession.getValue());
		ApiClient attacker = newClient();
		attacker.useSessionCookie(preLoginSession);
		assertProblem(attacker.get("/api/hello"), 401, "UNAUTHENTICATED");
	}

	@Test
	void passwordBeyondBcryptsLimitNeverMatchesByTruncation() {
		String seventyTwoBytes = "p".repeat(72);
		User user = createUser(Role.USER, seventyTwoBytes);

		assertProblem(newClient().login(user.getUsername(), seventyTwoBytes + "extra"), 401, "INVALID_CREDENTIALS");
		assertThat(newClient().login(user.getUsername(), seventyTwoBytes).getStatus()).isEqualTo(200);
	}

	@Test
	void loginWithoutCsrfTokenIsRejected() {
		User user = createUser(Role.USER);

		MockHttpServletResponse response = newClient().postWithoutCsrf("/api/auth/login",
				java.util.Map.of("username", user.getUsername(), "password", PASSWORD));

		assertProblem(response, 403, "CSRF_INVALID");
	}

	@Test
	void helloRequiresAnAuthenticatedSession() {
		assertProblem(newClient().get("/api/hello"), 401, "UNAUTHENTICATED");

		ApiClient forged = newClient();
		forged.useSessionCookie(new Cookie(ApiClient.SESSION_COOKIE, "bm90LWEtcmVhbC1zZXNzaW9u"));
		assertProblem(forged.get("/api/hello"), 401, "UNAUTHENTICATED");
	}

	@Test
	void meReturnsTheCurrentUser() {
		User user = createUser(Role.USER);
		ApiClient client = loggedIn(user);

		MockHttpServletResponse response = client.get("/api/me");

		assertThat(client.json(response).path("username").asString()).isEqualTo(user.getUsername());
		assertThat(client.json(response).path("id").asString()).isEqualTo(user.getId().toString());
	}

}
