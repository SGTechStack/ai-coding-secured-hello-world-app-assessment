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

import java.time.Duration;
import java.util.List;
import java.util.Map;

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
import com.example.securedhello.support.MutableClock;
import com.example.securedhello.support.RecordingEmailService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The per-username login rate limit through the HTTP API, with the Clock seam: more than 10 attempts
 * a minute against one username get 429 with {@code Retry-After}, before any credential check. Each
 * attempt comes from a different documentation-range address, so the per-address IP Throttle never
 * engages and the limit is shown to be per username. All test data is synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ MutableClock.Config.class, RecordingEmailService.Config.class })
class LoginRateLimitApiTest {

	private static final String PASSWORD = "Synthetic-Pass-42";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	private int nextAddress;

	@BeforeEach
	void oneRegisteredAccount() throws Exception {
		jdbc.update("DELETE FROM password_reset_tokens");
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM users");
		jdbc.update("DELETE FROM spring_session");
		Csrf csrf = csrf();
		mvc.perform(post("/api/register").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(
					Map.of("username", "testuser123", "email", "testuser123@test.example.com", "password", PASSWORD))))
			.andExpect(status().isCreated());
	}

	@Test
	void theEleventhAttemptInAMinuteGets429WithRetryAfterEvenWithTheCorrectPassword() throws Exception {
		for (int attempt = 1; attempt <= 10; attempt++) {
			login("testuser123", PASSWORD).andExpect(status().isOk());
		}

		login("testuser123", PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string("Retry-After", "6"))
			.andExpect(jsonPath("$.code").value("too_many_requests"))
			.andExpect(jsonPath("$.detail").value("too many requests"));
	}

	@Test
	void attemptsAreAllowedAgainOnceRetryAfterHasPassed() throws Exception {
		for (int attempt = 1; attempt <= 10; attempt++) {
			login("nosuchuser", PASSWORD).andExpect(status().isUnauthorized());
		}
		String retryAfter = login("nosuchuser", PASSWORD).andExpect(status().isTooManyRequests())
			.andReturn()
			.getResponse()
			.getHeader("Retry-After");

		clock.advance(Duration.ofSeconds(Long.parseLong(retryAfter)).minusMillis(1));
		login("nosuchuser", PASSWORD).andExpect(status().isTooManyRequests());
		clock.advance(Duration.ofMillis(1));
		login("nosuchuser", PASSWORD).andExpect(status().isUnauthorized());

		clock.advance(Duration.ofMinutes(1));
		for (int attempt = 1; attempt <= 10; attempt++) {
			login("nosuchuser", PASSWORD).andExpect(status().isUnauthorized());
		}
		login("nosuchuser", PASSWORD).andExpect(status().isTooManyRequests());
	}

	@Test
	void theLimitIsPerUsernameIgnoringCase() throws Exception {
		for (int attempt = 1; attempt <= 10; attempt++) {
			login((attempt % 2 == 0) ? "TestUser123" : "testuser123", PASSWORD).andExpect(status().isOk());
		}

		login("TESTUSER123", PASSWORD).andExpect(status().isTooManyRequests());
		login("nosuchuser", PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void aBreachEmitsAWarnAccessControlEventWithTheEndpointAndNoUsername() throws Exception {
		for (int attempt = 1; attempt <= 10; attempt++) {
			login("nosuchuser", PASSWORD).andExpect(status().isUnauthorized());
		}
		LogCapture capture = LogCapture.start();

		login("nosuchuser", PASSWORD).andExpect(status().isTooManyRequests());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "access-control"));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("WARN");
			assertThat(field(event, "event.outcome")).isEqualTo("failure");
			assertThat(field(event, "event.reason")).isEqualTo("rate_limited");
			assertThat(field(event, "url.path")).isEqualTo("/api/login");
			assertThat(field(event, "http.request.method")).isEqualTo("POST");
			assertThat(node(event, "user.id")).isNull();
			assertThat(field(event, "trace.id")).isNotBlank();
		});
		assertThat(capture.audit(hasField("event.action", "user-authentication"))).isEmpty();
		assertThat(capture.auditText()).noneMatch((line) -> line.contains("nosuchuser"));
		assertThat(capture.applicationText()).noneMatch((line) -> line.contains("nosuchuser"));
	}

	record Csrf(Cookie session, String token) {
	}

	private Csrf csrf() throws Exception {
		MvcResult result = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
		return new Csrf(result.getResponse().getCookie("SESSION"),
				JsonPath.read(result.getResponse().getContentAsString(), "$.token"));
	}

	private ResultActions login(String username, String password) throws Exception {
		Csrf csrf = csrf();
		String address = "192.0.2." + (1 + (nextAddress++ % 254));
		return mvc.perform(post("/api/login").with((request) -> {
			request.setRemoteAddr(address);
			return request;
		})
			.cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("username", username, "password", password))));
	}

}
