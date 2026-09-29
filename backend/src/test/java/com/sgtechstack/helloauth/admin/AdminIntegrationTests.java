package com.sgtechstack.helloauth.admin;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.JsonNode;

import com.sgtechstack.helloauth.support.ApiClient;
import com.sgtechstack.helloauth.support.IntegrationTest;
import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stories 8-11: admin user management, role enforcement and the self-action guard.
 */
class AdminIntegrationTests extends IntegrationTest {

	@Test
	void adminListsUsersWithoutPasswordHashes() {
		User admin = createUser(Role.ADMIN);
		User user = createUser(Role.USER);
		ApiClient client = loggedIn(admin);

		MockHttpServletResponse response = client.get("/api/admin/users?size=100");

		assertThat(response.getStatus()).isEqualTo(200);
		JsonNode page = client.json(response);
		JsonNode listed = null;
		for (JsonNode item : page.path("items")) {
			assertThat(item.propertyNames()).containsExactlyInAnyOrder("id", "username", "email", "role", "enabled",
					"createdAt");
			if (item.path("username").asString().equals(user.getUsername())) {
				listed = item;
			}
		}
		assertThat(listed).isNotNull();
		assertThat(listed.path("email").asString()).isEqualTo(user.getEmail());
		assertThat(listed.path("role").asString()).isEqualTo("USER");
		assertThat(listed.path("enabled").asBoolean()).isTrue();
		assertThat(listed.path("createdAt").asString()).isNotBlank();
		assertThat(ApiClient.body(response)).doesNotContain("$2a$").doesNotContainIgnoringCase("password");
		assertThat(page.path("totalItems").asLong()).isGreaterThanOrEqualTo(2);
	}

	@Test
	void userRoleGets403OnEveryAdminEndpoint() {
		ApiClient client = loggedIn(createUser(Role.USER));
		User target = createUser(Role.USER);
		String base = "/api/admin/users/" + target.getId();

		assertProblem(client.get("/api/admin/users"), 403, "FORBIDDEN");
		assertProblem(client.patch(base + "/status", Map.of("enabled", false)), 403, "FORBIDDEN");
		assertProblem(client.patch(base + "/role", Map.of("role", "ADMIN")), 403, "FORBIDDEN");
		assertProblem(client.delete(base), 403, "FORBIDDEN");

		User unchanged = reload(target);
		assertThat(unchanged.isEnabled()).isTrue();
		assertThat(unchanged.getRole()).isEqualTo(Role.USER);
	}

	@Test
	void anonymousGets401OnAdminEndpoints() {
		assertProblem(newClient().get("/api/admin/users"), 401, "UNAUTHENTICATED");
	}

	@Test
	void disablingAUserBlocksLoginAndEndsTheirSessions() {
		ApiClient admin = loggedIn(createUser(Role.ADMIN));
		User target = createUser(Role.USER);
		ApiClient targetSession = loggedIn(target);

		MockHttpServletResponse response = admin.patch("/api/admin/users/" + target.getId() + "/status",
				Map.of("enabled", false));

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(admin.json(response).path("enabled").asBoolean()).isFalse();
		assertProblem(targetSession.get("/api/hello"), 401, "UNAUTHENTICATED");
		assertProblem(newClient().login(target.getUsername(), PASSWORD), 401, "INVALID_CREDENTIALS");

		admin.patch("/api/admin/users/" + target.getId() + "/status", Map.of("enabled", true));
		assertThat(newClient().login(target.getUsername(), PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void roleChangesTakeEffectImmediately() {
		ApiClient admin = loggedIn(createUser(Role.ADMIN));
		User target = createUser(Role.USER);
		ApiClient before = loggedIn(target);

		MockHttpServletResponse promoted = admin.patch("/api/admin/users/" + target.getId() + "/role",
				Map.of("role", "ADMIN"));

		assertThat(admin.json(promoted).path("role").asString()).isEqualTo("ADMIN");
		assertProblem(before.get("/api/hello"), 401, "UNAUTHENTICATED");
		ApiClient asAdmin = loggedIn(target);
		assertThat(asAdmin.get("/api/admin/users").getStatus()).isEqualTo(200);

		admin.patch("/api/admin/users/" + target.getId() + "/role", Map.of("role", "USER"));

		// A demoted admin's existing session must not keep admin access.
		assertProblem(asAdmin.get("/api/admin/users"), 401, "UNAUTHENTICATED");
		assertProblem(loggedIn(target).get("/api/admin/users"), 403, "FORBIDDEN");
	}

	@Test
	void adminCanDeleteAnotherUser() {
		ApiClient admin = loggedIn(createUser(Role.ADMIN));
		User target = createUser(Role.USER);
		ApiClient targetSession = loggedIn(target);

		assertThat(admin.delete("/api/admin/users/" + target.getId()).getStatus()).isEqualTo(204);

		assertThat(this.users.findById(target.getId())).isEmpty();
		assertProblem(targetSession.get("/api/hello"), 401, "UNAUTHENTICATED");
		assertProblem(newClient().login(target.getUsername(), PASSWORD), 401, "INVALID_CREDENTIALS");
	}

	@Test
	void adminCannotDisableDemoteOrDeleteThemselves() {
		User self = createUser(Role.ADMIN);
		ApiClient admin = loggedIn(self);
		String base = "/api/admin/users/" + self.getId();

		assertProblem(admin.patch(base + "/status", Map.of("enabled", false)), 409, "SELF_ACTION_NOT_ALLOWED");
		assertProblem(admin.patch(base + "/role", Map.of("role", "USER")), 409, "SELF_ACTION_NOT_ALLOWED");
		assertProblem(admin.delete(base), 409, "SELF_ACTION_NOT_ALLOWED");

		User unchanged = reload(self);
		assertThat(unchanged.isEnabled()).isTrue();
		assertThat(unchanged.getRole()).isEqualTo(Role.ADMIN);
		assertThat(admin.get("/api/admin/users").getStatus()).isEqualTo(200);
	}

	@Test
	void unknownUserIs404() {
		ApiClient admin = loggedIn(createUser(Role.ADMIN));

		assertProblem(admin.delete("/api/admin/users/" + UUID.randomUUID()), 404, "USER_NOT_FOUND");
	}

	@Test
	void invalidInputIsRejected() {
		ApiClient admin = loggedIn(createUser(Role.ADMIN));
		User target = createUser(Role.USER);

		assertThat(admin.get("/api/admin/users?size=1000").getStatus()).isEqualTo(400);
		assertThat(admin.patch("/api/admin/users/" + target.getId() + "/role", Map.of("role", "ROOT")).getStatus())
			.isEqualTo(400);
		assertProblem(admin.patch("/api/admin/users/" + target.getId() + "/status", Map.of()), 400,
				"VALIDATION_FAILED");
	}

}
