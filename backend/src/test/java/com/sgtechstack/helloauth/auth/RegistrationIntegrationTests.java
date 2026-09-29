package com.sgtechstack.helloauth.auth;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletResponse;

import com.sgtechstack.helloauth.support.ApiClient;
import com.sgtechstack.helloauth.support.IntegrationTest;
import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 1: registration.
 */
class RegistrationIntegrationTests extends IntegrationTest {

	@Test
	void registersAnEnabledUserWithABcryptHash() {
		String username = uniqueUsername();

		MockHttpServletResponse response = register(username.toUpperCase(), username + "@Example.COM", PASSWORD);

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(ApiClient.body(response)).doesNotContain(PASSWORD);
		User user = this.users.findByUsername(username).orElseThrow();
		assertThat(user.getEmail()).isEqualTo(username + "@example.com");
		assertThat(user.getRole()).isEqualTo(Role.USER);
		assertThat(user.isEnabled()).isTrue();
		assertThat(user.getPasswordHash()).startsWith("$2a$").isNotEqualTo(PASSWORD);
		assertThat(this.passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
		assertThat(newClient().login(username, PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void duplicateUsernameIsRejectedIgnoringCase() {
		String username = uniqueUsername();
		register(username, username + "@example.com", PASSWORD);
		long before = this.users.count();

		MockHttpServletResponse response = register(username.toUpperCase(), "other-" + username + "@example.com",
				PASSWORD);

		assertProblem(response, 409, "REGISTRATION_CONFLICT");
		assertThat(ApiClient.body(response)).contains("\"username\":\"is already taken\"");
		assertThat(this.users.count()).isEqualTo(before);
	}

	@Test
	void duplicateEmailIsRejected() {
		String username = uniqueUsername();
		register(username, username + "@example.com", PASSWORD);
		long before = this.users.count();

		MockHttpServletResponse response = register(uniqueUsername(), username.toUpperCase() + "@example.com",
				PASSWORD);

		assertProblem(response, 409, "REGISTRATION_CONFLICT");
		assertThat(ApiClient.body(response)).contains("\"email\":\"is already registered\"");
		assertThat(this.users.count()).isEqualTo(before);
	}

	@Test
	void passwordShorterThanTwelveCharactersIsRejected() {
		String username = uniqueUsername();

		MockHttpServletResponse response = register(username, username + "@example.com", "Elevenchars");

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(ApiClient.body(response)).contains("\"password\":\"must be at least 12 characters")
			.doesNotContain("Elevenchars");
		assertThat(this.users.findByUsername(username)).isEmpty();
	}

	@Test
	void passwordLongerThan72BytesIsRejected() {
		String username = uniqueUsername();

		// 24 three-byte characters: 24 characters but 72+ bytes once "x" is added.
		MockHttpServletResponse response = register(username, username + "@example.com", "€".repeat(24) + "x");

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(this.users.findByUsername(username)).isEmpty();
	}

	@Test
	void malformedInputIsRejected() {
		assertProblem(register("a", "someone@example.com", PASSWORD), 400, "VALIDATION_FAILED");
		assertProblem(register("bad name!", "someone@example.com", PASSWORD), 400, "VALIDATION_FAILED");
		assertProblem(register(uniqueUsername(), "not-an-email", PASSWORD), 400, "VALIDATION_FAILED");
		assertThat(newClient().post("/api/auth/register", "{not json").getStatus()).isEqualTo(400);
	}

	private MockHttpServletResponse register(String username, String email, String password) {
		return newClient().post("/api/auth/register",
				Map.of("username", username, "email", email, "password", password));
	}

}
