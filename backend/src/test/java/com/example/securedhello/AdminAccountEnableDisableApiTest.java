package com.example.securedhello;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.example.securedhello.support.LogCapture;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code PATCH /api/admin/users/{id}/enabled}: an Admin disables or re-enables another Account.
 * Disabling ends the target's Sessions immediately and its later logins fail exactly like a wrong
 * password; re-enabling restores login without setting the required-password-change flag (ADR
 * 0001). The self-action guard and the last-Admin rule are enforced, both audited at WARN; every
 * success is audited at INFO with the before and after state. All test data is synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAccountEnableDisableApiTest {

	private static final String ADMIN = "testadmin123";

	private static final String OTHER_ADMIN = "testadmin456";

	private static final String USER = "testuser123";

	private static final String PASSWORD = "Synthetic-Pass-42";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private String adminId;

	private String otherAdminId;

	private String userId;

	@BeforeEach
	void twoAdminsAndOneUser() throws Exception {
		jdbc.update("DELETE FROM password_reset_tokens");
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM deleted_users");
		jdbc.update("DELETE FROM users");
		jdbc.update("DELETE FROM spring_session");
		register(ADMIN);
		register(OTHER_ADMIN);
		register(USER);
		jdbc.update("UPDATE users SET role = 'ADMIN' WHERE username IN (?, ?)", ADMIN, OTHER_ADMIN);
		adminId = idOf(ADMIN);
		otherAdminId = idOf(OTHER_ADMIN);
		userId = idOf(USER);
	}

	@Test
	void disablingEndsTheTargetsSessionAndLaterLoginsFailLikeAWrongPassword() throws Exception {
		Cookie userSession = loggedIn(USER);
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		String wrongPasswordBody = login(csrf(), USER, "Wrong-Pass-4242").andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();

		setEnabled(adminSession, adminCsrf, userId, false).andExpect(status().isOk());

		mvc.perform(get("/api/me").cookie(userSession)).andExpect(status().isUnauthorized());
		String afterDisableBody = login(csrf(), USER, PASSWORD).andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(afterDisableBody).isEqualTo(wrongPasswordBody);
	}

	@Test
	void reEnablingRestoresLoginWithoutSettingRequiredPasswordChange() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		setEnabled(adminSession, adminCsrf, userId, false).andExpect(status().isOk());

		setEnabled(adminSession, adminCsrf, userId, true).andExpect(status().isOk());

		login(csrf(), USER, PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.passwordChangeRequired").value(false));
	}

	@Test
	void anAdminCannotDisableTheirOwnAccount() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		LogCapture capture = LogCapture.start();

		setEnabled(adminSession, adminCsrf, adminId, false).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("self_action_forbidden"));

		assertThat(jdbc.queryForObject("SELECT enabled FROM users WHERE username = ?", Boolean.class, ADMIN)).isTrue();
		JsonNode event = capture.awaitAudit(hasField("event.reason", "self_action_forbidden")).get(0);
		assertThat(field(event, "log.level")).isEqualTo("WARN");
		assertThat(field(event, "event.action")).isEqualTo("user-administration");
		assertThat(field(event, "event.outcome")).isEqualTo("failure");
		assertThat(field(event, "user.id")).isEqualTo(adminId);
		assertThat(field(event, "target.user.id")).isEqualTo(adminId);
	}

	/**
	 * The acting Admin's own row is disabled directly (not through the API, which would end their
	 * Session), leaving Other Admin as the only enabled Admin; the acting Admin's Session, still
	 * carrying its Admin authority from login, is then used to disable Other Admin. Mirrors the DB
	 * state the concurrency test reaches through a real race (spec "the last-Admin rule... checked
	 * under a pessimistic row lock").
	 */
	@Test
	void disablingTheLastEnabledAdminIsRejected() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		jdbc.update("UPDATE users SET enabled = false WHERE username = ?", ADMIN);
		LogCapture capture = LogCapture.start();

		setEnabled(adminSession, adminCsrf, otherAdminId, false).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("last_admin"));

		assertThat(jdbc.queryForObject("SELECT enabled FROM users WHERE username = ?", Boolean.class, OTHER_ADMIN))
			.isTrue();
		JsonNode event = capture.awaitAudit(hasField("event.reason", "last_admin")).get(0);
		assertThat(field(event, "log.level")).isEqualTo("WARN");
		assertThat(field(event, "event.action")).isEqualTo("user-administration");
		assertThat(field(event, "target.user.id")).isEqualTo(otherAdminId);
	}

	@Test
	void disablingAUserNeverTriggersTheLastAdminRule() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);

		setEnabled(adminSession, adminCsrf, userId, false).andExpect(status().isOk());
	}

	@Test
	void anUnknownIdReturns404() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);

		setEnabled(adminSession, adminCsrf, UUID.randomUUID().toString(), false).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("not_found"));
	}

	@Test
	void aSuccessfulDisableIsAuditedAsAnInfoUserAdministrationEventWithTheBeforeAndAfterState() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		loggedIn(USER);
		LogCapture capture = LogCapture.start();

		setEnabled(adminSession, adminCsrf, userId, false).andExpect(status().isOk());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "user-administration"));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("INFO");
			assertThat(field(event, "event.outcome")).isEqualTo("success");
			assertThat(field(event, "user.id")).isEqualTo(adminId);
			assertThat(field(event, "target.user.id")).isEqualTo(userId);
			assertThat(field(event, "state.before.enabled")).isEqualTo("true");
			assertThat(field(event, "state.after.enabled")).isEqualTo("false");
			assertThat(field(event, "url.path")).isEqualTo("/api/admin/users/" + userId + "/enabled");
			assertThat(field(event, "http.request.method")).isEqualTo("PATCH");
			assertThat(field(event, "trace.id")).isNotBlank();
		});
		assertThat(capture.audit(hasField("event.action", "session-end")).stream()
			.anyMatch((event) -> "account_disabled".equals(field(event, "event.reason")))).isTrue();
	}

	private ResultActions setEnabled(Cookie session, Csrf csrf, String id, boolean enabled) throws Exception {
		return mvc.perform(patch("/api/admin/users/" + id + "/enabled").cookie(session)
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("enabled", enabled))));
	}

	private String idOf(String username) {
		return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username).toString();
	}

	private void register(String username) throws Exception {
		Csrf csrf = csrf(null);
		mvc.perform(post("/api/register").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(
					Map.of("username", username, "email", username + "@test.example.com", "password", PASSWORD))))
			.andExpect(status().isCreated());
	}

	private ResultActions login(Csrf csrf, String username, String password) throws Exception {
		return mvc.perform(post("/api/login").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("username", username, "password", password))));
	}

	private Cookie loggedIn(String username) throws Exception {
		return login(csrf(), username, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
	}

	record Csrf(Cookie session, String token) {
	}

	private Csrf csrf() throws Exception {
		return csrf(null);
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
