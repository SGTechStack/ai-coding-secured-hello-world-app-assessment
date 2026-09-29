package com.sgtechstack.helloauth.audit;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.sgtechstack.helloauth.support.ApiClient;
import com.sgtechstack.helloauth.support.IntegrationTest;
import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The PRD's audit events are logged with actor and target, and passwords never reach the log.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuditLoggingIntegrationTests extends IntegrationTest {

	@Test
	void authenticationEventsAreAuditedWithoutPasswords(CapturedOutput output) {
		String registeredPassword = "Registration-Secret-1";
		String typoPassword = "Typo-Secret-Value-2";
		String username = uniqueUsername();
		ApiClient client = newClient();

		client.post("/api/auth/register",
				Map.of("username", username, "email", username + "@example.com", "password", registeredPassword));
		client.post("/api/auth/register",
				Map.of("username", uniqueUsername(), "email", "x@example.com", "password", "short-secret"));
		for (int i = 0; i < 5; i++) {
			client.login(username, typoPassword);
		}
		client.login("password-typed-as-username", typoPassword);

		assertThat(output).contains("event=USER_REGISTERED target=" + username)
			.contains("event=LOGIN_FAILED target=" + username)
			.contains("reason=BAD_PASSWORD")
			.contains("event=ACCOUNT_LOCKED target=" + username)
			.contains("reason=UNKNOWN_USERNAME")
			.doesNotContain(registeredPassword)
			.doesNotContain(typoPassword)
			.doesNotContain("short-secret")
			.doesNotContain("password-typed-as-username");
	}

	@Test
	void adminActionsAreAuditedWithActorAndTarget(CapturedOutput output) {
		User admin = createUser(Role.ADMIN);
		User target = createUser(Role.USER);
		ApiClient client = loggedIn(admin);
		String base = "/api/admin/users/" + target.getId();

		client.patch(base + "/role", Map.of("role", "ADMIN"));
		client.patch(base + "/status", Map.of("enabled", false));
		client.patch(base + "/status", Map.of("enabled", true));
		client.delete(base);
		client.delete("/api/admin/users/" + admin.getId());

		String actorAndTarget = "actor=" + admin.getUsername() + " target=" + target.getUsername();
		assertThat(output).contains("event=USER_ROLE_CHANGED " + actorAndTarget)
			.contains("from=USER to=ADMIN")
			.contains("event=USER_DISABLED " + actorAndTarget)
			.contains("event=USER_ENABLED " + actorAndTarget)
			.contains("event=USER_DELETED " + actorAndTarget)
			.contains("event=ADMIN_ACTION_REJECTED actor=" + admin.getUsername());
	}

	@Test
	void accessDenialsAndUserListingAreAudited(CapturedOutput output) {
		User user = createUser(Role.USER);
		User admin = createUser(Role.ADMIN);

		loggedIn(user).get("/api/admin/users");
		newClient().postWithoutCsrf("/api/auth/login", Map.of("username", user.getUsername(), "password", "x"));
		loggedIn(admin).get("/api/admin/users?page=0&size=10");

		assertThat(output)
			.contains("event=ACCESS_DENIED actor=" + user.getUsername())
			.contains("reason=FORBIDDEN method=GET path=/api/admin/users")
			.contains("event=ACCESS_DENIED actor=-")
			.contains("reason=CSRF_INVALID method=POST path=/api/auth/login")
			.contains("event=USER_LIST_VIEWED actor=" + admin.getUsername())
			.contains("page=0 size=10");
	}

	@Test
	void passwordResetEventsAreAudited(CapturedOutput output) {
		User user = createUser(Role.USER);
		newClient().post("/api/auth/password-reset/request", Map.of("email", user.getEmail()));
		org.awaitility.Awaitility.await().until(() -> this.emails.lastSentTo(user.getEmail()).isPresent());
		String token = this.emails.lastSentTo(user.getEmail()).orElseThrow().token();

		newClient().post("/api/auth/password-reset/confirm",
				Map.of("token", token, "newPassword", "Reset-Secret-Value-3"));
		newClient().post("/api/auth/password-reset/confirm",
				Map.of("token", token, "newPassword", "Reset-Secret-Value-4"));

		assertThat(output).contains("event=PASSWORD_RESET_REQUESTED target=" + user.getUsername())
			.contains("event=PASSWORD_RESET_COMPLETED target=" + user.getUsername())
			.contains("event=PASSWORD_RESET_REJECTED target=" + user.getUsername())
			.contains("reason=TOKEN_ALREADY_USED")
			.doesNotContain("Reset-Secret-Value");
	}

}
