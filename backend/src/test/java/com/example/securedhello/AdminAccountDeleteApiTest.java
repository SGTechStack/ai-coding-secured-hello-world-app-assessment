package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
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
 * {@code DELETE /api/admin/users/{id}}: an Admin deletes another Account. The Account, its Password
 * History and its Reset Tokens go; a tombstone keeping its UUID, username, email, deletion time and
 * the deleting Admin stays indefinitely (ADR 0001), so its username can never be registered again
 * while its email can. The target's Sessions end at once. The self-action guard and the last-Admin
 * rule are enforced and audited at WARN; every success is audited at INFO. All test data is
 * synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RecordingEmailService.Config.class)
class AdminAccountDeleteApiTest {

	private static final String ADMIN = "testadmin123";

	private static final String OTHER_ADMIN = "testadmin456";

	private static final String USER = "testuser123";

	private static final String PASSWORD = "Synthetic-Pass-42";

	private static final String NEW_PASSWORD = "Synthetic-Pass-43";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	RecordingEmailService email;

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
		register(ADMIN, addressOf(ADMIN)).andExpect(status().isCreated());
		register(OTHER_ADMIN, addressOf(OTHER_ADMIN)).andExpect(status().isCreated());
		register(USER, addressOf(USER)).andExpect(status().isCreated());
		jdbc.update("UPDATE users SET role = 'ADMIN' WHERE username IN (?, ?)", ADMIN, OTHER_ADMIN);
		adminId = idOf(ADMIN);
		otherAdminId = idOf(OTHER_ADMIN);
		userId = idOf(USER);
		email.clear();
	}

	@Test
	void deleteWritesTheTombstoneRemovesTheAccountWithItsCredentialRowsAndEndsItsSessions() throws Exception {
		Cookie userSession = loggedIn(USER);
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		Instant before = Instant.now();

		deleteAccount(adminSession, adminCsrf, userId).andExpect(status().isNoContent());

		assertThat(jdbc.queryForList("SELECT * FROM users WHERE username = ?", USER)).isEmpty();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_history WHERE user_id = ?", Integer.class,
				UUID.fromString(userId)))
			.isZero();
		assertThat(jdbc.queryForList("SELECT * FROM deleted_users")).singleElement().satisfies((tombstone) -> {
			assertThat(tombstone.get("id").toString()).isEqualTo(userId);
			assertThat(tombstone.get("username")).isEqualTo(USER);
			assertThat(tombstone.get("email")).isEqualTo(addressOf(USER));
			assertThat(tombstone.get("deleted_by").toString()).isEqualTo(adminId);
			assertThat(((OffsetDateTime) tombstone.get("deleted_at")).toInstant()).isAfterOrEqualTo(before);
		});
		mvc.perform(get("/api/me").cookie(userSession)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/me").cookie(adminSession)).andExpect(status().isOk());
	}

	@Test
	void theDeletedAccountsPendingResetTokenNoLongerWorks() throws Exception {
		requestReset(addressOf(USER)).andExpect(status().isAccepted());
		String token = RecordingEmailService.tokenFrom(email.awaitResetLink());
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);

		deleteAccount(adminSession, adminCsrf, userId).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class)).isZero();
		confirmReset(token, NEW_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("token_invalid"));
	}

	@Test
	void aTombstonedUsernameIsRejectedWhileTheDeletedAccountsEmailCanBeReused() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		deleteAccount(adminSession, adminCsrf, userId).andExpect(status().isNoContent());

		register(USER, "reused-username@test.example.com").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("user_exist"))
			.andExpect(jsonPath("$.detail").value("user exist"));
		register("testuser789", addressOf(USER)).andExpect(status().isCreated());
	}

	@Test
	void anAdminCannotDeleteTheirOwnAccount() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		LogCapture capture = LogCapture.start();

		deleteAccount(adminSession, adminCsrf, adminId).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("self_action_forbidden"));

		assertThat(jdbc.queryForList("SELECT * FROM users WHERE username = ?", ADMIN)).hasSize(1);
		assertThat(jdbc.queryForList("SELECT * FROM deleted_users")).isEmpty();
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
	 * carrying its Admin authority from login, is then used to delete Other Admin.
	 */
	@Test
	void deletingTheLastEnabledAdminIsRejected() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		jdbc.update("UPDATE users SET enabled = false WHERE username = ?", ADMIN);
		LogCapture capture = LogCapture.start();

		deleteAccount(adminSession, adminCsrf, otherAdminId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("last_admin"));

		assertThat(jdbc.queryForList("SELECT * FROM users WHERE username = ?", OTHER_ADMIN)).hasSize(1);
		assertThat(jdbc.queryForList("SELECT * FROM deleted_users")).isEmpty();
		JsonNode event = capture.awaitAudit(hasField("event.reason", "last_admin")).get(0);
		assertThat(field(event, "log.level")).isEqualTo("WARN");
		assertThat(field(event, "event.action")).isEqualTo("user-administration");
		assertThat(field(event, "target.user.id")).isEqualTo(otherAdminId);
	}

	@Test
	void deletingADisabledAdminNeverTriggersTheLastAdminRule() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		jdbc.update("UPDATE users SET enabled = false WHERE username = ?", OTHER_ADMIN);

		deleteAccount(adminSession, adminCsrf, otherAdminId).andExpect(status().isNoContent());
	}

	@Test
	void anUnknownIdReturns404() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);

		deleteAccount(adminSession, adminCsrf, UUID.randomUUID().toString()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("not_found"));
	}

	@Test
	void aSuccessfulDeleteIsAuditedAsAnInfoUserAdministrationEventAndEndsTheTargetsSessionsWithAReason()
			throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		loggedIn(USER);
		LogCapture capture = LogCapture.start();

		deleteAccount(adminSession, adminCsrf, userId).andExpect(status().isNoContent());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "user-administration"));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("INFO");
			assertThat(field(event, "event.outcome")).isEqualTo("success");
			assertThat(field(event, "user.id")).isEqualTo(adminId);
			assertThat(field(event, "target.user.id")).isEqualTo(userId);
			assertThat(field(event, "state.before.deleted")).isEqualTo("false");
			assertThat(field(event, "state.after.deleted")).isEqualTo("true");
			assertThat(field(event, "url.path")).isEqualTo("/api/admin/users/" + userId);
			assertThat(field(event, "http.request.method")).isEqualTo("DELETE");
			assertThat(field(event, "trace.id")).isNotBlank();
		});
		assertThat(capture.audit(hasField("event.action", "session-end"))).anySatisfy((event) -> {
			assertThat(field(event, "event.reason")).isEqualTo("account_deleted");
			assertThat(field(event, "user.id")).isEqualTo(userId);
		});
	}

	/**
	 * The gap that criterion 2's Session sweep cannot close on its own. Spring Session JDBC indexes a
	 * Session under its principal only when the row is written at response commit, so an Account that is
	 * mid-login when the delete runs is not in {@code endAll}'s result set: it keeps a live Session
	 * carrying the authorities it was issued, and no operator can revoke it afterwards, because a repeat
	 * delete answers 404. The state that leaves behind is built directly here — a live Session, its index
	 * entry cleared, its Account row gone — rather than by racing a real login, which would be a timing
	 * test. The case that matters is a deleted *Admin*, whose stale Session still satisfies both the
	 * {@code /api/admin/**} URL rule and {@code @PreAuthorize("hasRole('ADMIN')")}.
	 */
	@Test
	void aSessionWhoseAccountIsGoneIsRefusedAndEndedOnAnAdminPathDespiteItsStaleAdminAuthority() throws Exception {
		Cookie staleAdminSession = loggedIn(OTHER_ADMIN);
		accountGoneUnderALiveSession(otherAdminId, staleAdminSession);
		LogCapture capture = LogCapture.start();

		mvc.perform(get("/api/admin/users").cookie(staleAdminSession))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("authentication_required"));

		assertThat(storedSessions(staleAdminSession)).isZero();
		JsonNode event = capture.awaitAudit(hasField("event.action", "session-end")).get(0);
		assertThat(field(event, "event.reason")).isEqualTo("account_deleted");
		assertThat(field(event, "user.id")).isEqualTo(otherAdminId);
	}

	/**
	 * The same on a path the Required Password Change allowed matcher lets through. {@code GET /csrf}
	 * answered 200 for a deleted Account before the existence check, because the matcher is evaluated
	 * before any Account lookup; this pins the check as sitting above it.
	 */
	@Test
	void aSessionWhoseAccountIsGoneIsRefusedAndEndedOnAPathTheAllowedMatcherLetsThrough() throws Exception {
		Cookie staleSession = loggedIn(USER);
		accountGoneUnderALiveSession(userId, staleSession);

		mvc.perform(get("/api/csrf").cookie(staleSession)).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("authentication_required"));

		assertThat(storedSessions(staleSession)).isZero();
	}

	/**
	 * {@code GET /me} relies on the same one check rather than a lookup of its own: the stale Session is
	 * ended there too, not just refused.
	 */
	@Test
	void aSessionWhoseAccountIsGoneIsRefusedAndEndedOnMe() throws Exception {
		Cookie staleSession = loggedIn(USER);
		accountGoneUnderALiveSession(userId, staleSession);

		mvc.perform(get("/api/me").cookie(staleSession)).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("authentication_required"));

		assertThat(storedSessions(staleSession)).isZero();
	}

	/** Removes the Account under a still-live Session, and with it the index entry a sweep would need. */
	private void accountGoneUnderALiveSession(String accountId, Cookie session) {
		jdbc.update("UPDATE spring_session SET principal_name = NULL WHERE session_id = ?", sessionId(session));
		jdbc.update("DELETE FROM password_history WHERE user_id = ?", UUID.fromString(accountId));
		jdbc.update("DELETE FROM users WHERE id = ?", UUID.fromString(accountId));
	}

	private Integer storedSessions(Cookie session) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM spring_session WHERE session_id = ?", Integer.class,
				sessionId(session));
	}

	private static String sessionId(Cookie cookie) {
		return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
	}

	private ResultActions deleteAccount(Cookie session, Csrf csrf, String id) throws Exception {
		return mvc.perform(delete("/api/admin/users/" + id).cookie(session).header("X-CSRF-TOKEN", csrf.token()));
	}

	private static String addressOf(String username) {
		return username + "@test.example.com";
	}

	private String idOf(String username) {
		return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username).toString();
	}

	private ResultActions register(String username, String address) throws Exception {
		Csrf csrf = csrf(null);
		return mvc.perform(post("/api/register").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON
				.writeValueAsString(Map.of("username", username, "email", address, "password", PASSWORD))));
	}

	private ResultActions requestReset(String address) throws Exception {
		Csrf csrf = csrf(null);
		return mvc.perform(post("/api/password-reset/request").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("email", address))));
	}

	private ResultActions confirmReset(String token, String newPassword) throws Exception {
		Csrf csrf = csrf(null);
		return mvc.perform(post("/api/password-reset/confirm").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("token", token, "newPassword", newPassword))));
	}

	private ResultActions login(Csrf csrf, String username, String password) throws Exception {
		return mvc.perform(post("/api/login").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("username", username, "password", password))));
	}

	private Cookie loggedIn(String username) throws Exception {
		return login(csrf(), username, PASSWORD).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");
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
