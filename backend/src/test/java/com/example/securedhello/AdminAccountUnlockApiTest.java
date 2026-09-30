package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
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

import com.example.securedhello.ratelimit.RateLimiters;
import com.example.securedhello.support.LogCapture;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /api/admin/users/{id}/unlock}: an Admin lifts a Lock so the holder can log in at once
 * with the correct password, and a fresh threshold of wrong passwords is needed to lock the Account
 * again. The self-action guard (a hijacked admin Session can't lift a lock protecting itself) is
 * enforced and audited at WARN; every success is audited at INFO with the before and after lock
 * state. All test data is synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAccountUnlockApiTest {

	private static final String ADMIN = "testadmin123";

	private static final String USER = "testuser123";

	private static final String PASSWORD = "Synthetic-Pass-42";

	private static final String WRONG_PASSWORD = "Wrong-Pass-4242";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	RateLimiters rateLimiters;

	private String adminId;

	private String userId;

	@BeforeEach
	void oneAdminAndOneUser() throws Exception {
		jdbc.update("DELETE FROM password_reset_tokens");
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM deleted_users");
		jdbc.update("DELETE FROM users");
		jdbc.update("DELETE FROM spring_session");
		register(ADMIN);
		register(USER);
		jdbc.update("UPDATE users SET role = 'ADMIN' WHERE username = ?", ADMIN);
		adminId = idOf(ADMIN);
		userId = idOf(USER);
	}

	@Test
	void unlockingALockedAccountClearsTheLockAndTheFailureCounterSoTheCorrectPasswordWorksAtOnce() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		lockTheAccount(USER);

		unlock(adminSession, adminCsrf, userId).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE username = ?", Integer.class,
				USER))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT locked_until FROM users WHERE username = ?", Timestamp.class, USER))
			.isNull();
		login(csrf(), USER, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void theFailureCounterStartsAgainFromZeroAfterAnUnlock() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		lockTheAccount(USER);
		unlock(adminSession, adminCsrf, userId).andExpect(status().isOk());
		// The lockout counter, not the per-username rate limit, is under test here.
		rateLimiters.resetAll();

		for (int attempt = 1; attempt <= 4; attempt++) {
			login(csrf(), USER, WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
		login(csrf(), USER, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void unlockingAnAccountThatIsNotLockedStillSucceeds() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);

		unlock(adminSession, adminCsrf, userId).andExpect(status().isOk());
	}

	@Test
	void anAdminCannotUnlockTheirOwnAccount() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		jdbc.update("UPDATE users SET locked_until = ? WHERE username = ?",
				Timestamp.from(Instant.now().plusSeconds(600)), ADMIN);
		LogCapture capture = LogCapture.start();

		unlock(adminSession, adminCsrf, adminId).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("self_action_forbidden"));

		assertThat(jdbc.queryForObject("SELECT locked_until FROM users WHERE username = ?", Timestamp.class, ADMIN))
			.isNotNull();
		JsonNode event = capture.awaitAudit(hasField("event.reason", "self_action_forbidden")).get(0);
		assertThat(field(event, "log.level")).isEqualTo("WARN");
		assertThat(field(event, "event.action")).isEqualTo("user-administration");
		assertThat(field(event, "event.outcome")).isEqualTo("failure");
		assertThat(field(event, "user.id")).isEqualTo(adminId);
		assertThat(field(event, "target.user.id")).isEqualTo(adminId);
	}

	@Test
	void anUnknownIdReturns404() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);

		unlock(adminSession, adminCsrf, UUID.randomUUID().toString()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("not_found"));
	}

	@Test
	void aSuccessfulUnlockIsAuditedAsAnInfoUserAdministrationEventWithTheBeforeAndAfterLockState() throws Exception {
		Cookie adminSession = loggedIn(ADMIN);
		Csrf adminCsrf = csrf(adminSession);
		lockTheAccount(USER);
		LogCapture capture = LogCapture.start();

		unlock(adminSession, adminCsrf, userId).andExpect(status().isOk());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "user-administration"));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("INFO");
			assertThat(field(event, "event.outcome")).isEqualTo("success");
			assertThat(field(event, "user.id")).isEqualTo(adminId);
			assertThat(field(event, "target.user.id")).isEqualTo(userId);
			assertThat(field(event, "state.before.locked")).isEqualTo("true");
			assertThat(field(event, "state.after.locked")).isEqualTo("false");
			assertThat(field(event, "url.path")).isEqualTo("/api/admin/users/" + userId + "/unlock");
			assertThat(field(event, "http.request.method")).isEqualTo("POST");
			assertThat(field(event, "trace.id")).isNotBlank();
		});
	}

	/** Five wrong passwords followed by a sixth (correct) attempt, refused because the Account is Locked. */
	private void lockTheAccount(String username) throws Exception {
		for (int attempt = 1; attempt <= 5; attempt++) {
			login(csrf(), username, WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
		login(csrf(), username, PASSWORD).andExpect(status().isUnauthorized());
	}

	private ResultActions unlock(Cookie session, Csrf csrf, String id) throws Exception {
		return mvc.perform(
				post("/api/admin/users/" + id + "/unlock").cookie(session).header("X-CSRF-TOKEN", csrf.token()));
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
