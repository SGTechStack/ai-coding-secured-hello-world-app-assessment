package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/**
 * {@code POST /client-events}: what the SPA may tell the operator about itself (story 111). A report
 * carries a fixed kind, a path and, for a timed kind, a duration; anything else is refused, so a
 * report cannot carry user data or error details. Reports need no Session and no CSRF token (ADR 0002)
 * and are rate-limited per client address.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientEventApiTest {

	private static final String ADDRESS = "192.0.2.111";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void aVisitorCanReportARenderErrorAndItReachesTheApplicationLog() throws Exception {
		LogCapture logs = LogCapture.start();

		report("{\"kind\":\"RENDER_ERROR\",\"path\":\"/login\"}").andExpect(status().isNoContent());

		List<JsonNode> lines = logs.application(hasField("event.action", "client-error"));
		assertThat(lines).hasSize(1);
		JsonNode line = lines.getFirst();
		assertThat(field(line, "log.level")).isEqualTo("WARN");
		assertThat(field(line, "client.event.kind")).isEqualTo("RENDER_ERROR");
		assertThat(field(line, "client.url.path")).isEqualTo("/login");
		assertThat(field(line, "trace.id")).isNotBlank();
	}

	@Test
	void aTimedReportIsAcceptedWithoutBeingCountedAsAnError() throws Exception {
		LogCapture logs = LogCapture.start();

		report("{\"kind\":\"PAGE_LOAD\",\"path\":\"/\",\"durationMs\":1234}").andExpect(status().isNoContent());

		// A page timing is a number to watch on the management port, not a failure to read about.
		assertThat(logs.application(hasField("event.action", "client-error"))).isEmpty();
	}

	@Test
	void aFieldTheApiDoesNotKnowIsDroppedRatherThanRecorded() throws Exception {
		LogCapture logs = LogCapture.start();

		report("{\"kind\":\"RENDER_ERROR\",\"path\":\"/\",\"message\":\"SELECT password_hash FROM users\"}")
			.andExpect(status().isNoContent());

		assertThat(logs.application(hasField("event.action", "client-error"))).hasSize(1);
		assertThat(logs.applicationText()).isNotEmpty().noneMatch((line) -> line.contains("SELECT password_hash"));
	}

	@Test
	void aReportNeedsNoCsrfTokenAndCreatesNoSession() throws Exception {
		int before = sessionRows();

		MvcResult result = report("{\"kind\":\"RENDER_ERROR\",\"path\":\"/\"}").andExpect(status().isNoContent())
			.andReturn();

		// The exemption (ADR 0002) exists for this: bootstrapping a token would persist a Session for
		// every Visitor and crawler, since the CSRF repository stores the token in one.
		assertThat(result.getResponse().getCookie("SESSION")).isNull();
		assertThat(sessionRows()).isEqualTo(before);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			// An unknown kind, so the set of reportable events cannot be widened from the browser.
			"{\"kind\":\"SOMETHING_ELSE\",\"path\":\"/\"}",
			// A path that is not a path, carries a query string or a fragment, or is absurdly long.
			"{\"kind\":\"RENDER_ERROR\",\"path\":\"login\"}",
			"{\"kind\":\"RENDER_ERROR\",\"path\":\"/reset-password?token=abc\"}",
			"{\"kind\":\"RENDER_ERROR\",\"path\":\"/reset-password#token=abc\"}",
			"{\"kind\":\"RENDER_ERROR\",\"path\":\"/a\\r\\nforged-log-line\"}",
			"{\"kind\":\"RENDER_ERROR\"}",
			// An error kind carrying a duration, or a timed kind without one.
			"{\"kind\":\"RENDER_ERROR\",\"path\":\"/\",\"durationMs\":10}",
			"{\"kind\":\"PAGE_LOAD\",\"path\":\"/\"}",
			"{\"kind\":\"PAGE_LOAD\",\"path\":\"/\",\"durationMs\":-1}",
			"{\"kind\":\"PAGE_LOAD\",\"path\":\"/\",\"durationMs\":3600001}" })
	void aReportThatIsNotExactlyWhatTheApiAcceptsIsRefused(String body) throws Exception {
		LogCapture logs = LogCapture.start();

		report(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));

		assertThat(logs.application(hasField("event.action", "client-error"))).isEmpty();
	}

	@Test
	void reportsBeyondTheLimitAreRefusedWithRetryAfter() throws Exception {
		for (int i = 0; i < 60; i++) {
			report("{\"kind\":\"UNCAUGHT_ERROR\",\"path\":\"/\"}").andExpect(status().isNoContent());
		}

		report("{\"kind\":\"UNCAUGHT_ERROR\",\"path\":\"/\"}").andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.code").value("too_many_requests"));
	}

	/** Posts a report exactly as the SPA does: no cookie, no CSRF token (ADR 0002). */
	private ResultActions report(String body) throws Exception {
		return mvc.perform(post("/api/client-events").with((request) -> {
			request.setRemoteAddr(ADDRESS);
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** Rows Spring Session has persisted. A report must never add one (ADR 0002). */
	private int sessionRows() {
		return this.jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
	}

}
