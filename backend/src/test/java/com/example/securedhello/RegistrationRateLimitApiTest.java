package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.securedhello.support.LogCapture;
import com.example.securedhello.support.MutableClock;
import com.example.securedhello.support.RecordingEmailService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The registration rate limit through the HTTP API, with the Clock seam: at most 10 registrations an
 * hour from one client address, then 429 with {@code Retry-After}. Addresses are from the
 * documentation ranges and all other test data is synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ MutableClock.Config.class, RecordingEmailService.Config.class })
class RegistrationRateLimitApiTest {

	private static final String ADDRESS = "192.0.2.30";

	private static final String OTHER = "198.51.100.40";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@BeforeEach
	void emptyAccounts() {
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM users");
		jdbc.update("DELETE FROM spring_session");
	}

	@Test
	void theEleventhRegistrationInAnHourGets429WithRetryAfter() throws Exception {
		for (int n = 1; n <= 10; n++) {
			register(ADDRESS, n).andExpect(status().isCreated());
		}

		register(ADDRESS, 11).andExpect(status().isTooManyRequests())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string("Retry-After", "360"))
			.andExpect(jsonPath("$.code").value("too_many_requests"))
			.andExpect(jsonPath("$.detail").value("too many requests"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(10);
	}

	@Test
	void theLimitIsPerAddressAndRefillsOverTheHour() throws Exception {
		for (int n = 1; n <= 10; n++) {
			register(ADDRESS, n).andExpect(status().isCreated());
		}
		register(OTHER, 11).andExpect(status().isCreated());

		clock.advance(Duration.ofMinutes(6));
		register(ADDRESS, 12).andExpect(status().isCreated());
		register(ADDRESS, 13).andExpect(status().isTooManyRequests());
	}

	@Test
	void aBreachEmitsAWarnAccessControlEventWithAnIpHashAndNoAddress() throws Exception {
		for (int n = 1; n <= 10; n++) {
			register(ADDRESS, n).andExpect(status().isCreated());
		}
		LogCapture capture = LogCapture.start();

		register(ADDRESS, 11).andExpect(status().isTooManyRequests());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "access-control"));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("WARN");
			assertThat(field(event, "event.reason")).isEqualTo("rate_limited");
			assertThat(field(event, "source.ip_hash")).matches("[0-9a-f]{64}");
			assertThat(field(event, "url.path")).isEqualTo("/api/register");
		});
		assertThat(capture.auditText()).noneMatch((line) -> line.contains(ADDRESS));
		assertThat(capture.applicationText()).noneMatch((line) -> line.contains(ADDRESS));
	}

	private ResultActions register(String address, int n) throws Exception {
		MvcResult csrf = mvc.perform(from(address, get("/api/csrf"))).andExpect(status().isOk()).andReturn();
		return mvc.perform(from(address, post("/api/register")).cookie(csrf.getResponse().getCookie("SESSION"))
			.header("X-CSRF-TOKEN", (String) JsonPath.read(csrf.getResponse().getContentAsString(), "$.token"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("username", "testuser" + n, "email",
					"testuser" + n + "@test.example.com", "password", "Synthetic-Pass-42"))));
	}

	private static MockHttpServletRequestBuilder from(String address, MockHttpServletRequestBuilder request) {
		return request.with((servletRequest) -> {
			servletRequest.setRemoteAddr(address);
			return servletRequest;
		});
	}

}
