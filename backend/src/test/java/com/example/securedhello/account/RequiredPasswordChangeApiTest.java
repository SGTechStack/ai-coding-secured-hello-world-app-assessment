package com.example.securedhello.account;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.example.securedhello.support.LogCapture;
import com.example.securedhello.support.RecordingEmailService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Required password change, driven through the HTTP API like the SPA (Seam 1), with the audit
 * destination read back through {@link LogCapture} (Seam 4).
 * <p>
 * The fixture recreates the real Bootstrap Admin with the real initializer, so every scenario starts
 * from an Account whose {@code password_change_required} was set the way a first deployment sets it.
 * Tests that need a working Admin complete that required change first ({@link #adminReady()}), which
 * is the fixture step every other test class skips by deleting the Bootstrap Admin outright. All test
 * data is synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RecordingEmailService.Config.class)
class RequiredPasswordChangeApiTest {

	/** From {@code application-test.properties}: the configured Bootstrap Admin, lowercased at creation. */
	private static final String ADMIN = "testadmin";

	private static final String ADMIN_PASSWORD = "Synthetic-Admin-Pass-42";

	private static final String ADMIN_NEW_PASSWORD = "Synthetic-Admin-Pass-43";

	private static final String USER = "testuser123";

	private static final String USER_EMAIL = "testuser123@test.example.com";

	private static final String USER_PASSWORD = "Synthetic-Pass-42";

	private static final String USER_NEW_PASSWORD = "Synthetic-Pass-43";

	private static final String PASSWORD_CHANGE_REQUIRED = "password_change_required";

	private static final String ENFORCEMENT = "password-change-enforcement";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	RecordingEmailService email;

	@Autowired
	BootstrapAdminInitializer bootstrapAdmin;

	private String adminId;

	@BeforeEach
	void onlyTheBootstrapAdmin() {
		jdbc.update("DELETE FROM password_reset_tokens");
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM users");
		jdbc.update("DELETE FROM spring_session");
		bootstrapAdmin.run(null);
		adminId = idOf(ADMIN);
		email.clear();
	}

	@Test
	void theBootstrapAdminsFirstLoginIsRefusedEverywhereExceptItsOwnAccountAndCsrf() throws Exception {
		Cookie session = loggedIn(ADMIN, ADMIN_PASSWORD, true);

		mvc.perform(get("/api/hello").cookie(session))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(PASSWORD_CHANGE_REQUIRED));
		mvc.perform(get("/api/admin/users").cookie(session))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(PASSWORD_CHANGE_REQUIRED));
		mvc.perform(get("/api/me").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passwordChangeRequired").value(true));
		mvc.perform(get("/api/csrf").cookie(session)).andExpect(status().isOk());
	}

	@Test
	void reportingABrowserErrorWorksWhileAPasswordChangeIsRequiredAndIsNotAudited() throws Exception {
		Cookie session = loggedIn(ADMIN, ADMIN_PASSWORD, true);
		LogCapture capture = LogCapture.start();

		// Public, and it says nothing about the Account, so refusing it would widen no control — it would
		// only write an enforcement event per page load and dilute a real security signal.
		mvc.perform(post("/api/client-events").cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"RENDER_ERROR\",\"path\":\"/password-change\"}"))
			.andExpect(status().isNoContent());

		assertThat(capture.audit(hasField("event.action", ENFORCEMENT))).isEmpty();
	}

	@Test
	void logoutWorksWhileAPasswordChangeIsRequired() throws Exception {
		Cookie session = loggedIn(ADMIN, ADMIN_PASSWORD, true);

		mvc.perform(post("/api/logout").cookie(session).header("X-CSRF-TOKEN", csrf(session).token()))
			.andExpect(status().isOk());
	}

	@Test
	void aPasswordChangeClearsTheRequirementSoTheAccountCanUseTheAppAgain() throws Exception {
		Cookie session = loggedIn(ADMIN, ADMIN_PASSWORD, true);

		changePassword(session, ADMIN_PASSWORD, ADMIN_NEW_PASSWORD).andExpect(status().isOk());

		assertThat(passwordChangeRequired(ADMIN)).isFalse();
		Cookie after = loggedIn(ADMIN, ADMIN_NEW_PASSWORD, false);
		mvc.perform(get("/api/me").cookie(after))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passwordChangeRequired").value(false));
		mvc.perform(get("/api/hello").cookie(after)).andExpect(status().isOk());
		mvc.perform(get("/api/admin/users").cookie(after)).andExpect(status().isOk());
	}

	@Test
	void requiringAChangeOnAnotherAccountEndsItsSessionsAndBlocksItUntilItChangesItsPassword() throws Exception {
		register(USER, USER_EMAIL, USER_PASSWORD);
		String userId = idOf(USER);
		Cookie userSession = loggedIn(USER, USER_PASSWORD, false);
		mvc.perform(get("/api/hello").cookie(userSession)).andExpect(status().isOk());
		Caller admin = adminReady();

		requirePasswordChange(admin, userId).andExpect(status().isOk());

		assertThat(passwordChangeRequired(USER)).isTrue();
		// The Session it held is gone, so the holder must log in again.
		mvc.perform(get("/api/hello").cookie(userSession)).andExpect(status().isUnauthorized());
		Cookie blocked = loggedIn(USER, USER_PASSWORD, true);
		mvc.perform(get("/api/hello").cookie(blocked))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(PASSWORD_CHANGE_REQUIRED));

		changePassword(blocked, USER_PASSWORD, USER_NEW_PASSWORD).andExpect(status().isOk());

		assertThat(passwordChangeRequired(USER)).isFalse();
		mvc.perform(get("/api/hello").cookie(loggedIn(USER, USER_NEW_PASSWORD, false))).andExpect(status().isOk());
	}

	@Test
	void aCompletedResetClearsTheRequirement() throws Exception {
		register(USER, USER_EMAIL, USER_PASSWORD);
		String userId = idOf(USER);
		Caller admin = adminReady();
		requirePasswordChange(admin, userId).andExpect(status().isOk());

		confirmReset(requestResetToken(), USER_NEW_PASSWORD).andExpect(status().isOk());

		assertThat(passwordChangeRequired(USER)).isFalse();
		mvc.perform(get("/api/hello").cookie(loggedIn(USER, USER_NEW_PASSWORD, false))).andExpect(status().isOk());
	}

	/**
	 * Require-password-change is deliberately outside the self-action guard and the last-Admin rule
	 * ({@code spec.md:198} scopes those to disable, role change, unlock and delete), so an Admin can require
	 * it of themselves and is logged straight out by their own action.
	 */
	@Test
	void anAdminCanRequireAPasswordChangeOnTheirOwnAccountAndLosesTheirOwnSessions() throws Exception {
		Caller admin = adminReady();
		mvc.perform(get("/api/hello").cookie(admin.session())).andExpect(status().isOk());

		requirePasswordChange(admin, adminId).andExpect(status().isOk());

		assertThat(passwordChangeRequired(ADMIN)).isTrue();
		// Their own Session went with the target's, so they are back at login, not merely refused 403.
		mvc.perform(get("/api/hello").cookie(admin.session())).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/me").cookie(admin.session())).andExpect(status().isUnauthorized());
		Cookie again = loggedIn(ADMIN, ADMIN_NEW_PASSWORD, true);
		mvc.perform(get("/api/admin/users").cookie(again))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(PASSWORD_CHANGE_REQUIRED));
	}

	@Test
	void requiringAChangeThatIsAlreadyRequiredSucceedsAndAuditsTheFlagAsAlreadySet() throws Exception {
		register(USER, USER_EMAIL, USER_PASSWORD);
		String userId = idOf(USER);
		Caller admin = adminReady();
		requirePasswordChange(admin, userId).andExpect(status().isOk());
		LogCapture capture = LogCapture.start();

		requirePasswordChange(admin, userId).andExpect(status().isOk());

		assertThat(passwordChangeRequired(USER)).isTrue();
		List<JsonNode> events = capture.awaitAudit(hasField("event.action", ENFORCEMENT));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "event.outcome")).isEqualTo("success");
			assertThat(field(event, "event.reason")).isEqualTo("required");
			assertThat(field(event, "user.id")).isEqualTo(adminId);
			assertThat(field(event, "target.user.id")).isEqualTo(userId);
			assertThat(field(event, "state.before.change_required")).isEqualTo("true");
			assertThat(field(event, "state.after.change_required")).isEqualTo("true");
		});
	}

	@Test
	void aUserCannotRequireAPasswordChange() throws Exception {
		register(USER, USER_EMAIL, USER_PASSWORD);
		Cookie userSession = loggedIn(USER, USER_PASSWORD, false);
		Caller user = new Caller(userSession, csrf(userSession).token());

		requirePasswordChange(user, adminId).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("access_denied"));

		assertThat(passwordChangeRequired(USER)).isFalse();
	}

	@Test
	void anUnknownIdReturns404() throws Exception {
		Caller admin = adminReady();

		requirePasswordChange(admin, UUID.randomUUID().toString()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("not_found"));
	}

	@Test
	void settingTheFlagIsAuditedAtInfoWithTheActingAdminAndTheTarget() throws Exception {
		register(USER, USER_EMAIL, USER_PASSWORD);
		String userId = idOf(USER);
		Caller admin = adminReady();
		LogCapture capture = LogCapture.start();

		requirePasswordChange(admin, userId).andExpect(status().isOk());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", ENFORCEMENT));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("INFO");
			assertThat(field(event, "event.outcome")).isEqualTo("success");
			assertThat(field(event, "event.reason")).isEqualTo("required");
			assertThat(field(event, "user.id")).isEqualTo(adminId);
			assertThat(field(event, "target.user.id")).isEqualTo(userId);
			assertThat(field(event, "state.before.change_required")).isEqualTo("false");
			assertThat(field(event, "state.after.change_required")).isEqualTo("true");
			assertThat(field(event, "url.path")).isEqualTo("/api/admin/users/" + userId + "/require-password-change");
			assertThat(field(event, "http.request.method")).isEqualTo("POST");
			assertThat(field(event, "trace.id")).isNotBlank();
		});
	}

	@Test
	void clearingTheFlagIsAuditedAtInfo() throws Exception {
		Cookie session = loggedIn(ADMIN, ADMIN_PASSWORD, true);
		LogCapture capture = LogCapture.start();

		changePassword(session, ADMIN_PASSWORD, ADMIN_NEW_PASSWORD).andExpect(status().isOk());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", ENFORCEMENT));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("INFO");
			assertThat(field(event, "event.outcome")).isEqualTo("success");
			assertThat(field(event, "event.reason")).isEqualTo("cleared");
			assertThat(field(event, "user.id")).isEqualTo(adminId);
			assertThat(field(event, "state.before.change_required")).isEqualTo("true");
			assertThat(field(event, "state.after.change_required")).isEqualTo("false");
			assertThat(field(event, "trace.id")).isNotBlank();
		});
	}

	@Test
	void aResetClearingTheFlagIsAuditedAtInfo() throws Exception {
		register(USER, USER_EMAIL, USER_PASSWORD);
		String userId = idOf(USER);
		Caller admin = adminReady();
		requirePasswordChange(admin, userId).andExpect(status().isOk());
		String token = requestResetToken();
		LogCapture capture = LogCapture.start();

		confirmReset(token, USER_NEW_PASSWORD).andExpect(status().isOk());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", ENFORCEMENT));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("INFO");
			assertThat(field(event, "event.reason")).isEqualTo("cleared");
			assertThat(field(event, "user.id")).isEqualTo(userId);
			assertThat(field(event, "state.before.change_required")).isEqualTo("true");
			assertThat(field(event, "state.after.change_required")).isEqualTo("false");
		});
	}

	@Test
	void aRequestRefusedBecauseOfTheFlagIsAuditedAtWarn() throws Exception {
		Cookie session = loggedIn(ADMIN, ADMIN_PASSWORD, true);
		LogCapture capture = LogCapture.start();

		mvc.perform(get("/api/hello").cookie(session)).andExpect(status().isForbidden());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", ENFORCEMENT));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("WARN");
			assertThat(field(event, "event.outcome")).isEqualTo("failure");
			assertThat(field(event, "event.reason")).isEqualTo(PASSWORD_CHANGE_REQUIRED);
			assertThat(field(event, "user.id")).isEqualTo(adminId);
			assertThat(field(event, "url.path")).isEqualTo("/api/hello");
			assertThat(field(event, "http.request.method")).isEqualTo("GET");
			assertThat(field(event, "trace.id")).isNotBlank();
		});
	}

	/** A logged-in caller: its Session cookie and a CSRF token fetched for that Session. */
	record Caller(Cookie session, String csrfToken) {
	}

	/**
	 * The Bootstrap Admin with its required password change completed and logged in again, which is
	 * what every scenario that needs a working Admin starts from.
	 */
	private Caller adminReady() throws Exception {
		changePassword(loggedIn(ADMIN, ADMIN_PASSWORD, true), ADMIN_PASSWORD, ADMIN_NEW_PASSWORD)
			.andExpect(status().isOk());
		Cookie session = loggedIn(ADMIN, ADMIN_NEW_PASSWORD, false);
		return new Caller(session, csrf(session).token());
	}

	private ResultActions requirePasswordChange(Caller admin, String targetId) throws Exception {
		return mvc.perform(post("/api/admin/users/" + targetId + "/require-password-change").cookie(admin.session())
			.header("X-CSRF-TOKEN", admin.csrfToken()));
	}

	private ResultActions changePassword(Cookie session, String currentPassword, String newPassword) throws Exception {
		return mvc.perform(patch("/api/me/password").cookie(session)
			.header("X-CSRF-TOKEN", csrf(session).token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(
					Map.of("currentPassword", currentPassword, "newPassword", newPassword))));
	}

	/** Requests a reset for the User and returns the Reset Token from the recorded link (Seam 3). */
	private String requestResetToken() throws Exception {
		Csrf csrf = csrf(null);
		mvc.perform(post("/api/password-reset/request").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("email", USER_EMAIL))))
			.andExpect(status().isAccepted());
		return RecordingEmailService.tokenFrom(email.awaitResetLink());
	}

	private ResultActions confirmReset(String token, String newPassword) throws Exception {
		Csrf csrf = csrf(null);
		return mvc.perform(post("/api/password-reset/confirm").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("token", token, "newPassword", newPassword))));
	}

	private boolean passwordChangeRequired(String username) {
		return jdbc.queryForObject("SELECT password_change_required FROM users WHERE username = ?", Boolean.class,
				username);
	}

	private String idOf(String username) {
		return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username).toString();
	}

	private void register(String username, String userEmail, String password) throws Exception {
		Csrf csrf = csrf(null);
		mvc.perform(post("/api/register").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(
					Map.of("username", username, "email", userEmail, "password", password))))
			.andExpect(status().isCreated());
	}

	private Cookie loggedIn(String username, String password, boolean changeRequired) throws Exception {
		Csrf csrf = csrf(null);
		MvcResult result = mvc.perform(post("/api/login").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("username", username, "password", password))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passwordChangeRequired").value(changeRequired))
			.andReturn();
		return result.getResponse().getCookie("SESSION");
	}

	record Csrf(Cookie session, String token) {
	}

	private Csrf csrf(Cookie session) throws Exception {
		MvcResult result = mvc.perform((session != null) ? get("/api/csrf").cookie(session) : get("/api/csrf"))
			.andExpect(status().isOk())
			.andReturn();
		Cookie issued = result.getResponse().getCookie("SESSION");
		String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
		return new Csrf((issued != null) ? issued : session, token);
	}

}
