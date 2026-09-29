package com.sgtechstack.helloauth.auth;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import com.sgtechstack.helloauth.support.ApiClient;
import com.sgtechstack.helloauth.support.IntegrationTest;
import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 4: logout ends the server-side session.
 */
class LogoutIntegrationTests extends IntegrationTest {

	@Test
	void logoutInvalidatesTheSessionAndClearsTheCookie() {
		ApiClient client = loggedIn(createUser(Role.USER));
		Cookie capturedBeforeLogout = client.sessionCookie();

		MockHttpServletResponse response = client.logout();

		assertThat(response.getStatus()).isEqualTo(204);
		assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
			.anySatisfy(cookie -> assertThat(cookie).startsWith(ApiClient.SESSION_COOKIE + "=;").contains("Max-Age=0"));
		assertThat(client.sessionCookie()).isNull();

		ApiClient replay = newClient();
		replay.useSessionCookie(capturedBeforeLogout);
		assertProblem(replay.get("/api/hello"), 401, "UNAUTHENTICATED");
		assertProblem(replay.get("/api/me"), 401, "UNAUTHENTICATED");
	}

	@Test
	void logoutRequiresCsrfTokenSoOtherSitesCannotForceIt() {
		ApiClient client = loggedIn(createUser(Role.USER));

		assertProblem(client.postWithoutCsrf("/api/auth/logout", null), 403, "CSRF_INVALID");
		assertThat(client.get("/api/hello").getStatus()).isEqualTo(200);
	}

	@Test
	void logoutOnlyEndsTheCurrentSession() {
		User user = createUser(Role.USER);
		ApiClient laptop = loggedIn(user);
		ApiClient phone = loggedIn(user);

		laptop.logout();

		assertThat(phone.get("/api/hello").getStatus()).isEqualTo(200);
	}

}
