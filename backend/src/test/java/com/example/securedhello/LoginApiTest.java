package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static com.example.securedhello.support.LogCapture.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * Login, the hello screen's endpoints, the own-Account endpoint and logout, driven through the
 * HTTP API like the SPA: {@code GET /csrf} for a Session and token, then JSON posts carrying the
 * token. All test data is synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginApiTest {

	static final String PASSWORD = "Synthetic-Pass-42";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	/** CSRF tokens and Session IDs this test was given; none may reach a log line. */
	private final List<String> secrets = new ArrayList<>();

	@BeforeEach
	void emptyAccountsAndSessions() {
		jdbc.update("DELETE FROM password_reset_tokens");
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM users");
		jdbc.update("DELETE FROM spring_session");
	}

	@Test
	void accountHolderLogsInAndGetsTheirOwnAccount() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		UUID id = jdbc.queryForObject("SELECT id FROM users", UUID.class);

		login(csrf(), "TestUser123", PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id.toString()))
			.andExpect(jsonPath("$.username").value("testuser123"))
			.andExpect(jsonPath("$.email").value("testuser123@test.example.com"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.passwordChangeRequired").value(false))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void unknownUsernameAndWrongPasswordGetTheIdenticalAuthenticationFailedBody() throws Exception {
		register("testuser123", "testuser123@test.example.com");

		String wrongPassword = login(csrf(), "testuser123", "Wrong-Pass-4242").andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("authentication_failed"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String unknownUsername = login(csrf(), "nosuchuser", PASSWORD).andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String overlongPassword = login(csrf(), "testuser123", "Aa1!" + "€".repeat(30))
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(unknownUsername).isEqualTo(wrongPassword);
		assertThat(overlongPassword).isEqualTo(wrongPassword);
	}

	@Test
	void missingOrOverlongLoginFieldsAreValidationErrors() throws Exception {
		Csrf csrf = csrf();
		mvc.perform(post("/api/login").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation"));

		login(csrf(), "a".repeat(33), PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation"));
	}

	@Test
	void loginRequiresACsrfToken() throws Exception {
		mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("username", "testuser123", "password", PASSWORD))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("csrf_invalid"));
	}

	@Test
	void loginReplacesTheSessionIdSoThePreLoginIdIsUseless() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		Csrf before = csrf();

		Cookie after = login(before, "testuser123", PASSWORD).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");

		assertThat(after).isNotNull();
		assertThat(after.getValue()).isNotEqualTo(before.session().getValue());
		mvc.perform(get("/api/me").cookie(before.session())).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/me").cookie(after)).andExpect(status().isOk());
	}

	@Test
	void aSecondLoginEndsTheFirstSession() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		Cookie first = loggedIn("testuser123");

		Cookie second = loggedIn("testuser123");

		mvc.perform(get("/api/me").cookie(first)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/me").cookie(second)).andExpect(status().isOk());
	}

	@Test
	void loggingInAgainInTheSameBrowserKeepsThatSession() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		Cookie first = loggedIn("testuser123");

		Cookie second = login(csrf(first), "testuser123", PASSWORD).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");

		mvc.perform(get("/api/me").cookie(second)).andExpect(status().isOk());
	}

	@Test
	void aCsrfTokenIssuedBeforeLoginIsRejectedAfterIt() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		Csrf before = csrf();
		Cookie session = login(before, "testuser123", PASSWORD).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");

		mvc.perform(post("/api/logout").cookie(session).header("X-CSRF-TOKEN", before.token()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("csrf_invalid"));

		Csrf fresh = csrf(session);
		assertThat(fresh.token()).isNotEqualTo(before.token());
	}

	@Test
	void logoutReturns200WithClearSiteDataAndDeletesTheSessionCookie() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		Csrf session = csrf(loggedIn("testuser123"));

		MvcResult result = logout(session).andExpect(status().isOk())
			.andExpect(header().string("Clear-Site-Data", "\"cache\",\"cookies\",\"storage\""))
			.andReturn();

		Cookie deleted = result.getResponse().getCookie("SESSION");
		assertThat(deleted).isNotNull();
		assertThat(deleted.getMaxAge()).isZero();
	}

	@Test
	void aSessionCookieReplayedAfterLogoutIsRejected() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		Csrf session = csrf(loggedIn("testuser123"));
		logout(session).andExpect(status().isOk());

		mvc.perform(get("/api/hello").cookie(session.session())).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/me").cookie(session.session())).andExpect(status().isUnauthorized());
	}

	@Test
	void aCsrfTokenIssuedBeforeLogoutIsRejectedAfterIt() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		Csrf beforeLogout = csrf(loggedIn("testuser123"));
		logout(beforeLogout).andExpect(status().isOk());
		Csrf afterLogout = csrf();

		login(new Csrf(afterLogout.session(), beforeLogout.token()), "testuser123", PASSWORD)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("csrf_invalid"));
		login(afterLogout, "testuser123", PASSWORD).andExpect(status().isOk());
	}

	@Test
	void visitorLoggingOutGets401() throws Exception {
		logout(csrf()).andExpect(status().isUnauthorized());
	}

	@Test
	void successfulLoginEmitsAnInfoUserAuthenticationEventKeyedByTheAccountUuid() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		String id = jdbc.queryForObject("SELECT id FROM users", UUID.class).toString();
		LogCapture capture = LogCapture.start();

		loggedIn("testuser123");

		JsonNode event = capture.awaitAudit(hasField("event.action", "user-authentication")).get(0);
		assertThat(field(event, "log.level")).isEqualTo("INFO");
		assertThat(field(event, "event.outcome")).isEqualTo("success");
		assertThat(field(event, "authentication.method")).isEqualTo("password");
		assertThat(field(event, "user.id")).isEqualTo(id);
		assertThat(field(event, "url.path")).isEqualTo("/api/login");
		assertThat(field(event, "http.request.method")).isEqualTo("POST");
		assertThat(field(event, "trace.id")).isNotBlank();
		assertNoSecretsLogged(capture);
	}

	@Test
	void failedLoginEmitsAWarnEventWithoutIdentityButWithTheSessionHash() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		Csrf csrf = csrf();
		LogCapture capture = LogCapture.start();

		login(csrf, "testuser123", "Wrong-Pass-4242").andExpect(status().isUnauthorized());
		login(csrf, "nosuchuser", PASSWORD).andExpect(status().isUnauthorized());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "user-authentication"));
		assertThat(events).hasSize(2).allSatisfy((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("WARN");
			assertThat(field(event, "event.outcome")).isEqualTo("failure");
			assertThat(field(event, "event.reason")).isEqualTo("authentication_failed");
			assertThat(field(event, "authentication.method")).isEqualTo("password");
			assertThat(node(event, "user.id")).isNull();
			assertThat(field(event, "session.hash")).matches("[0-9a-f]{64}");
		});
		assertThat(field(events.get(0), "session.hash")).isEqualTo(field(events.get(1), "session.hash"));
		assertNoSecretsLogged(capture);
		assertThat(String.join("\n", capture.auditText())).doesNotContain("nosuchuser").doesNotContain("Wrong-Pass-4242");
	}

	@Test
	void logoutEmitsAnInfoUserLogoutEvent() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		String id = jdbc.queryForObject("SELECT id FROM users", UUID.class).toString();
		Csrf session = csrf(loggedIn("testuser123"));
		LogCapture capture = LogCapture.start();

		logout(session).andExpect(status().isOk());

		JsonNode event = capture.awaitAudit(hasField("event.action", "user-logout")).get(0);
		assertThat(field(event, "log.level")).isEqualTo("INFO");
		assertThat(field(event, "event.outcome")).isEqualTo("success");
		assertThat(field(event, "user.id")).isEqualTo(id);
		assertNoSecretsLogged(capture);
	}

	@Test
	void authenticatedRequestLogLinesCarryTheAccountUuid() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		String id = jdbc.queryForObject("SELECT id FROM users", UUID.class).toString();
		Cookie session = loggedIn("testuser123");
		LogCapture capture = LogCapture.start();

		mvc.perform(get("/api/hello").cookie(session)).andExpect(status().isOk());

		List<JsonNode> requestLines = capture.application(hasField("url.path", "/api/hello"));
		assertThat(requestLines).isNotEmpty().last().satisfies((end) -> assertThat(field(end, "user.id")).isEqualTo(id));
		assertNoSecretsLogged(capture);
	}

	@Test
	void anAuthenticationSystemFailureIsA500AndAnErrorAuditEvent() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		Csrf csrf = csrf();
		LogCapture capture = LogCapture.start();
		jdbc.execute("ALTER TABLE users RENAME TO users_unavailable");
		try {
			login(csrf, "testuser123", PASSWORD).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("internal_error"));
		}
		finally {
			jdbc.execute("ALTER TABLE users_unavailable RENAME TO users");
		}

		JsonNode event = capture.awaitAudit(hasField("event.action", "user-authentication")).get(0);
		assertThat(field(event, "log.level")).isEqualTo("ERROR");
		assertThat(field(event, "event.outcome")).isEqualTo("failure");
		assertThat(field(event, "event.reason")).isEqualTo("authentication_system_failure");
		assertThat(node(event, "user.id")).isNull();
		assertThat(capture.application(hasField("log.level", "ERROR"))).hasSize(1)
			.allSatisfy((line) -> assertThat(field(line, "error.category")).isEqualTo("database"));
		assertNoSecretsLogged(capture);
	}

	@Test
	void helloGreetsTheLoggedInAccountHolderAsPlainText() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		Cookie session = loggedIn("testuser123");

		mvc.perform(get("/api/hello").cookie(session))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
			.andExpect(content().string("Hello, testuser123"));
	}

	@Test
	void meReturnsOnlyTheCallersOwnAccountAndTakesNoIdFromTheRequest() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		register("testuser456", "testuser456@test.example.com");
		UUID otherId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'testuser456'", UUID.class);
		Cookie session = loggedIn("testuser123");

		mvc.perform(get("/api/me").cookie(session).queryParam("id", otherId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("testuser123"))
			.andExpect(jsonPath("$.email").value("testuser123@test.example.com"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
		mvc.perform(get("/api/me/" + otherId).cookie(session)).andExpect(status().isForbidden());
	}

	@Test
	void visitorGets401FromMe() throws Exception {
		mvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("authentication_required"));
	}

	@Test
	void meGets401OnceTheAccountNoLongerExists() throws Exception {
		register("testuser123", "testuser123@test.example.com");
		Cookie session = loggedIn("testuser123");
		jdbc.update("DELETE FROM password_reset_tokens");
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM users");

		mvc.perform(get("/api/me").cookie(session))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("authentication_required"));
	}

	record Csrf(Cookie session, String token) {
	}

	/** Logs in with a fresh Session and returns the authenticated Session's cookie. */
	Cookie loggedIn(String username) throws Exception {
		return login(csrf(), username, PASSWORD).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");
	}

	/** A Session cookie and CSRF token, fetched the way the SPA does. */
	Csrf csrf() throws Exception {
		return csrf(null);
	}

	Csrf csrf(Cookie session) throws Exception {
		MvcResult result = mvc.perform((session != null) ? get("/api/csrf").cookie(session) : get("/api/csrf"))
			.andExpect(status().isOk())
			.andReturn();
		Cookie issued = rememberSecrets(result);
		String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
		secrets.add(token);
		return new Csrf((issued != null) ? issued : session, token);
	}

	ResultActions login(Csrf csrf, String username, String password) throws Exception {
		return mvc
			.perform(post("/api/login").cookie(csrf.session())
				.header("X-CSRF-TOKEN", csrf.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(Map.of("username", username, "password", password))))
			.andDo(this::rememberSecrets);
	}

	/** Records a Session ID issued in the response, raw and as the base64 cookie value. */
	private Cookie rememberSecrets(MvcResult result) {
		Cookie cookie = result.getResponse().getCookie("SESSION");
		if (cookie != null && !cookie.getValue().isEmpty()) {
			secrets.add(cookie.getValue());
			secrets.add(new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8));
		}
		return cookie;
	}

	/**
	 * No password, username, email, CSRF token or Session ID (raw or as the cookie value) in any
	 * log line.
	 */
	private void assertNoSecretsLogged(LogCapture capture) {
		String logs = String.join("\n", capture.auditText()) + String.join("\n", capture.applicationText());
		assertThat(logs).doesNotContain(PASSWORD)
			.doesNotContain("testuser123")
			.doesNotContain("test.example.com");
		assertThat(secrets).isNotEmpty().allSatisfy((secret) -> assertThat(logs).doesNotContain(secret));
	}

	ResultActions logout(Csrf csrf) throws Exception {
		return mvc.perform(post("/api/logout").cookie(csrf.session()).header("X-CSRF-TOKEN", csrf.token()));
	}

	void register(String username, String email) throws Exception {
		Csrf csrf = csrf();
		mvc.perform(post("/api/register").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("username", username, "email", email, "password", PASSWORD))))
			.andExpect(status().isCreated());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username))
			.isEqualTo(1);
	}

}
