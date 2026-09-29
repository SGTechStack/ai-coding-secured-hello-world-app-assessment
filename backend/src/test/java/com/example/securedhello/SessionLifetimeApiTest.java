package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.example.securedhello.security.SessionControl;
import com.example.securedhello.support.LogCapture;
import com.example.securedhello.support.MutableClock;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Session lifetime rules through the HTTP API, with the Clock seam: 15-minute idle timeout, 8-hour
 * absolute timeout, a failed login ending the carried Session, and the {@code session-end} audit
 * event. All test data is synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClock.Config.class)
class SessionLifetimeApiTest {

	private static final String PASSWORD = "Synthetic-Pass-42";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@Autowired
	SessionControl sessionControl;

	private String accountId;

	@BeforeEach
	void oneRegisteredAccount() throws Exception {
		jdbc.update("DELETE FROM password_reset_tokens");
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM users");
		jdbc.update("DELETE FROM spring_session");
		Csrf csrf = csrf(null);
		mvc.perform(post("/api/register").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(
					Map.of("username", "testuser123", "email", "testuser123@test.example.com", "password", PASSWORD))))
			.andExpect(status().isCreated());
		accountId = jdbc.queryForObject("SELECT id FROM users", UUID.class).toString();
	}

	@Test
	void aSessionUsedWithinTheIdleTimeoutStaysValid() throws Exception {
		Cookie session = loggedIn();

		clock.advance(Duration.ofMinutes(14));
		me(session).andExpect(status().isOk());
		clock.advance(Duration.ofMinutes(14));
		me(session).andExpect(status().isOk());
	}

	@Test
	void anIdleSessionIsRejectedAfter15Minutes() throws Exception {
		Cookie session = loggedIn();
		me(session).andExpect(status().isOk());

		clock.advance(Duration.ofMinutes(15));

		me(session).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("authentication_required"));
		clock.advance(Duration.ofMinutes(-10));
		me(session).andExpect(status().isUnauthorized());
	}

	@Test
	void aBusySessionIsRejectedAfter8Hours() throws Exception {
		Cookie session = loggedIn();

		for (int minutes = 10; minutes < 8 * 60; minutes += 10) {
			clock.advance(Duration.ofMinutes(10));
			me(session).andExpect(status().isOk());
		}
		clock.advance(Duration.ofMinutes(10));

		mvc.perform(get("/api/hello").cookie(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void anExpiredSessionEmitsASessionEndEvent() throws Exception {
		Cookie idle = loggedIn();
		clock.advance(Duration.ofMinutes(15));
		LogCapture capture = LogCapture.start();

		me(idle).andExpect(status().isUnauthorized());

		JsonNode idleEnd = capture.awaitAudit(hasField("event.action", "session-end")).get(0);
		assertThat(field(idleEnd, "log.level")).isEqualTo("INFO");
		assertThat(field(idleEnd, "event.outcome")).isEqualTo("success");
		assertThat(field(idleEnd, "event.reason")).isEqualTo("idle_timeout");
		assertThat(field(idleEnd, "user.id")).isEqualTo(accountId);
		assertThat(field(idleEnd, "trace.id")).isNotBlank();

		Cookie busy = loggedIn();
		for (int minutes = 10; minutes < 8 * 60; minutes += 10) {
			clock.advance(Duration.ofMinutes(10));
			me(busy).andExpect(status().isOk());
		}
		clock.advance(Duration.ofMinutes(10));
		LogCapture absolute = LogCapture.start();
		me(busy).andExpect(status().isUnauthorized());
		JsonNode absoluteEnd = absolute.awaitAudit(hasField("event.action", "session-end")).get(0);
		assertThat(field(absoluteEnd, "event.reason")).isEqualTo("absolute_timeout");
		assertThat(field(absoluteEnd, "user.id")).isEqualTo(accountId);
		assertNoIdentityLogged(capture);
	}

	@Test
	void aSecondLoginEmitsASessionEndEventForTheSessionItEnds() throws Exception {
		loggedIn();
		LogCapture capture = LogCapture.start();

		loggedIn();

		JsonNode event = capture.awaitAudit(hasField("event.action", "session-end")).get(0);
		assertThat(field(event, "log.level")).isEqualTo("INFO");
		assertThat(field(event, "event.reason")).isEqualTo("new_login");
		assertThat(field(event, "user.id")).isEqualTo(accountId);
		assertNoIdentityLogged(capture);
	}

	@Test
	void aFailedLoginEndsTheSessionTheRequestCarried() throws Exception {
		Cookie session = loggedIn();
		Csrf carried = csrf(session);
		LogCapture capture = LogCapture.start();

		login(carried, "Wrong-Pass-4242").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("authentication_failed"));

		me(session).andExpect(status().isUnauthorized());
		JsonNode event = capture.awaitAudit(hasField("event.action", "session-end")).get(0);
		assertThat(field(event, "event.reason")).isEqualTo("login_failed");
		assertThat(field(event, "user.id")).isEqualTo(accountId);
		assertNoIdentityLogged(capture);
	}

	@Test
	void aFailedLoginWithoutAnAuthenticatedSessionEndsNoSession() throws Exception {
		Csrf visitor = csrf(null);
		LogCapture capture = LogCapture.start();

		login(visitor, "Wrong-Pass-4242").andExpect(status().isUnauthorized());
		login(visitor, PASSWORD).andExpect(status().isOk());

		assertThat(capture.audit(hasField("event.action", "session-end"))).isEmpty();
	}

	@Test
	void aVisitorSessionKeepsTheIdleLifetimeAndALoggedInSessionGetsTheAbsoluteOne() throws Exception {
		Csrf visitor = csrf(null);
		assertThat(storedMaxInactiveSeconds(visitor.session())).isEqualTo(15 * 60);

		Cookie session = login(visitor, PASSWORD).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");

		assertThat(storedMaxInactiveSeconds(session)).isEqualTo(8 * 60 * 60);
	}

	@Test
	void aSecondLoginRecordsAnAlreadyIdleSessionAsIdleTimeout() throws Exception {
		loggedIn();
		clock.advance(Duration.ofMinutes(15));
		LogCapture capture = LogCapture.start();

		loggedIn();

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "session-end"));
		assertThat(events).hasSize(1);
		assertThat(field(events.get(0), "event.reason")).isEqualTo("idle_timeout");
		assertThat(field(events.get(0), "user.id")).isEqualTo(accountId);
	}

	@Test
	void endAllFromAnotherRequestEndsEverySessionOfTheAccount() throws Exception {
		Cookie session = loggedIn();
		LogCapture capture = LogCapture.start();

		sessionControl.endAll(UUID.fromString(accountId), "password_change", new MockHttpServletRequest(),
				new MockHttpServletResponse());

		me(session).andExpect(status().isUnauthorized());
		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "session-end"));
		assertThat(events).hasSize(1);
		assertThat(field(events.get(0), "log.level")).isEqualTo("INFO");
		assertThat(field(events.get(0), "event.reason")).isEqualTo("password_change");
		assertThat(field(events.get(0), "user.id")).isEqualTo(accountId);
	}

	@Test
	void endAllFromARequestCarryingThatSessionEndsItToo() throws Exception {
		Cookie session = loggedIn();
		MockHttpSession carried = new MockHttpSession(null, sessionId(session));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setSession(carried);
		LogCapture capture = LogCapture.start();

		sessionControl.endAll(UUID.fromString(accountId), "password_change", request, new MockHttpServletResponse());

		assertThat(carried.isInvalid()).isTrue();
		me(session).andExpect(status().isUnauthorized());
		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "session-end"));
		assertThat(events).hasSize(1);
		assertThat(field(events.get(0), "event.reason")).isEqualTo("password_change");
		assertThat(field(events.get(0), "user.id")).isEqualTo(accountId);
	}

	@Test
	void endAllRecordsASessionPastItsAbsoluteLimitAsAbsoluteTimeout() throws Exception {
		Cookie session = loggedIn();
		clock.advance(Duration.ofHours(8));
		LogCapture capture = LogCapture.start();

		sessionControl.endAll(UUID.fromString(accountId), "password_change", new MockHttpServletRequest(),
				new MockHttpServletResponse());

		me(session).andExpect(status().isUnauthorized());
		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "session-end"));
		assertThat(events).hasSize(1);
		assertThat(field(events.get(0), "event.reason")).isEqualTo("absolute_timeout");
	}

	private static String sessionId(Cookie cookie) {
		return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
	}

	private Integer storedMaxInactiveSeconds(Cookie cookie) {
		return jdbc.queryForObject("SELECT max_inactive_interval FROM spring_session WHERE session_id = ?",
				Integer.class, sessionId(cookie));
	}

	record Csrf(Cookie session, String token) {
	}

	private Cookie loggedIn() throws Exception {
		return login(csrf(null), PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
	}

	private Csrf csrf(Cookie session) throws Exception {
		MvcResult result = mvc.perform((session != null) ? get("/api/csrf").cookie(session) : get("/api/csrf"))
			.andExpect(status().isOk())
			.andReturn();
		Cookie issued = result.getResponse().getCookie("SESSION");
		String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
		return new Csrf((issued != null) ? issued : session, token);
	}

	private ResultActions login(Csrf csrf, String password) throws Exception {
		return mvc.perform(post("/api/login").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("username", "testuser123", "password", password))));
	}

	private ResultActions me(Cookie session) throws Exception {
		return mvc.perform(get("/api/me").cookie(session));
	}

	private static void assertNoIdentityLogged(LogCapture capture) {
		String logs = String.join("\n", List.of(String.join("\n", capture.auditText()),
				String.join("\n", capture.applicationText())));
		assertThat(logs).doesNotContain("testuser123").doesNotContain(PASSWORD);
	}

}
