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
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * The IP Throttle through the HTTP API, with the Clock seam: 20 failed logins from one client address
 * within 15 minutes, across any usernames, block that address for 15 minutes. The block is
 * independent of Account lockout in both directions. Addresses are from the documentation ranges and
 * all other test data is synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ MutableClock.Config.class, RecordingEmailService.Config.class })
class IpThrottleApiTest {

	private static final String ATTACKER = "192.0.2.10";

	private static final String OTHER = "198.51.100.20";

	private static final String PASSWORD = "Synthetic-Pass-42";

	private static final String WRONG_PASSWORD = "Wrong-Pass-4242";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@Value("${app.ip-hash.key}")
	String ipHashKey;

	@BeforeEach
	void oneRegisteredAccount() throws Exception {
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM users");
		jdbc.update("DELETE FROM spring_session");
		Csrf csrf = csrf(OTHER);
		mvc.perform(from(OTHER, post("/api/register")).cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(
					Map.of("username", "testuser123", "email", "testuser123@test.example.com", "password", PASSWORD))))
			.andExpect(status().isCreated());
	}

	@Test
	void twentyFailuresAcrossUsernamesBlockTheAddressEvenForACorrectPassword() throws Exception {
		sprayFailures(ATTACKER, 19);
		login(ATTACKER, "testuser123", PASSWORD).andExpect(status().isOk());
		sprayFailures(ATTACKER, 1);

		login(ATTACKER, "testuser123", PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string("Retry-After", "900"))
			.andExpect(jsonPath("$.code").value("too_many_requests"))
			.andExpect(jsonPath("$.detail").value("too many requests"));
	}

	@Test
	void failuresOlderThanTheWindowDoNotCount() throws Exception {
		sprayFailures(ATTACKER, 10);
		clock.advance(Duration.ofMinutes(15));
		sprayFailures(ATTACKER, 19);

		login(ATTACKER, "testuser123", PASSWORD).andExpect(status().isOk());
	}

	@Test
	void theBlockLiftsAfter15Minutes() throws Exception {
		sprayFailures(ATTACKER, 20);

		clock.advance(Duration.ofMinutes(15).minusSeconds(1));
		login(ATTACKER, "testuser123", PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "1"));
		clock.advance(Duration.ofSeconds(1));
		login(ATTACKER, "testuser123", PASSWORD).andExpect(status().isOk());
	}

	@Test
	void forwardedHeadersAreIgnored() throws Exception {
		sprayFailures(ATTACKER, 20);

		login(ATTACKER, "testuser123", PASSWORD, "203.0.113.99").andExpect(status().isTooManyRequests());
	}

	@Test
	void aThrottleNeverLocksAnAccountOrBlocksOtherAddresses() throws Exception {
		sprayFailures(ATTACKER, 20);
		for (int attempt = 1; attempt <= 10; attempt++) {
			login(ATTACKER, "testuser123", WRONG_PASSWORD).andExpect(status().isTooManyRequests());
		}

		login(OTHER, "testuser123", PASSWORD).andExpect(status().isOk());
	}

	@Test
	void aLockedAccountDoesNotThrottleItsAddressOrOthers() throws Exception {
		for (int attempt = 1; attempt <= 5; attempt++) {
			login(ATTACKER, "testuser123", WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
		login(OTHER, "testuser123", PASSWORD).andExpect(status().isUnauthorized());

		login(OTHER, "nosuchuser", PASSWORD).andExpect(status().isUnauthorized());
		login(ATTACKER, "nosuchuser", PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void aBlockedAttemptEmitsAWarnAccessControlEventWithTheKeyedIpHashAndNoAddress() throws Exception {
		sprayFailures(ATTACKER, 20);
		LogCapture capture = LogCapture.start();

		login(ATTACKER, "testuser123", PASSWORD, ATTACKER).andExpect(status().isTooManyRequests());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "access-control"));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("WARN");
			assertThat(field(event, "event.outcome")).isEqualTo("failure");
			assertThat(field(event, "event.reason")).isEqualTo("ip_throttled");
			assertThat(field(event, "source.ip_hash")).isEqualTo(hmacSha256(ATTACKER));
			assertThat(field(event, "url.path")).isEqualTo("/api/login");
			assertThat(node(event, "user.id")).isNull();
			assertThat(field(event, "trace.id")).isNotBlank();
		});
		assertThat(capture.audit(hasField("event.action", "user-authentication"))).isEmpty();
		assertThat(capture.auditText()).noneMatch((line) -> line.contains(ATTACKER));
		assertThat(capture.applicationText()).noneMatch((line) -> line.contains(ATTACKER));
	}

	@Test
	void noLogLineContainsTheClientAddressWhileTheThrottleEngages() throws Exception {
		LogCapture capture = LogCapture.start();

		sprayFailures(ATTACKER, 20);
		login(ATTACKER, "testuser123", PASSWORD).andExpect(status().isTooManyRequests());

		capture.awaitAudit(hasField("event.reason", "ip_throttled"));
		assertThat(capture.auditText()).isNotEmpty().noneMatch((line) -> line.contains(ATTACKER));
		assertThat(capture.applicationText()).isNotEmpty().noneMatch((line) -> line.contains(ATTACKER));
	}

	/** Failed logins from one address, spread over usernames so no per-username limit is hit. */
	private void sprayFailures(String address, int count) throws Exception {
		for (int attempt = 1; attempt <= count; attempt++) {
			login(address, "nosuchuser" + attempt, WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
	}

	private String hmacSha256(String address) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(ipHashKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(address.getBytes(StandardCharsets.UTF_8)));
	}

	record Csrf(Cookie session, String token) {
	}

	private Csrf csrf(String address) throws Exception {
		MvcResult result = mvc.perform(from(address, get("/api/csrf"))).andExpect(status().isOk()).andReturn();
		return new Csrf(result.getResponse().getCookie("SESSION"),
				JsonPath.read(result.getResponse().getContentAsString(), "$.token"));
	}

	private ResultActions login(String address, String username, String password) throws Exception {
		return login(address, username, password, null);
	}

	private ResultActions login(String address, String username, String password, String forwardedFor)
			throws Exception {
		Csrf csrf = csrf(address);
		MockHttpServletRequestBuilder request = from(address, post("/api/login")).cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("username", username, "password", password)));
		if (forwardedFor != null) {
			request.header("X-Forwarded-For", forwardedFor).header("Forwarded", "for=" + forwardedFor);
		}
		return mvc.perform(request);
	}

	private static MockHttpServletRequestBuilder from(String address, MockHttpServletRequestBuilder request) {
		return request.with((servletRequest) -> {
			servletRequest.setRemoteAddr(address);
			return servletRequest;
		});
	}

}
