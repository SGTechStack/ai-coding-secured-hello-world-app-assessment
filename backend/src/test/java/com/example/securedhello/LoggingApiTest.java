package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static com.example.securedhello.support.LogCapture.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.logging.AccountIdentified;
import com.example.securedhello.support.LogCapture;

import tools.jackson.databind.JsonNode;

/**
 * Structured logging through the HTTP API: every line is ECS JSON with service and trace fields,
 * requests are logged without leaking data, unexpected errors are logged once, and CSRF rejections
 * reach the audit log. Assertions read the real log files through {@link LogCapture}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoggingApiTest.FailingEndpoint.class)
class LoggingApiTest {

	private static final String RFC_3339_UTC8 = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+08:00";

	private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	private static final String SECRET_EMAIL = "testuser123@test.example.com";

	@Autowired
	MockMvc mvc;

	@Test
	void everyRequestLineIsEcsJsonWithServiceTraceAndCorrelationFields() throws Exception {
		LogCapture capture = LogCapture.start();

		mvc.perform(get("/api/csrf")).andExpect(status().isOk());

		List<JsonNode> lines = capture.application();
		assertThat(lines).isNotEmpty();
		for (JsonNode line : lines) {
			assertThat(field(line, "@timestamp")).matches(RFC_3339_UTC8);
			assertThat(field(line, "service.name")).isEqualTo("secured-hello-world");
			assertThat(field(line, "service.version")).isNotBlank();
			assertThat(field(line, "service.environment")).isNotBlank();
			assertThat(field(line, "log.level")).isNotBlank();
			assertThat(field(line, "message")).isNotBlank();
		}
		List<JsonNode> requestLines = requestLines(lines, "/api/csrf");
		assertThat(requestLines).hasSize(2);
		for (JsonNode line : requestLines) {
			assertThat(field(line, "trace.id")).matches("[0-9a-f]{16,32}");
			assertThat(field(line, "span.id")).matches("[0-9a-f]{16}");
			assertThat(field(line, "correlation.id")).matches(UUID_PATTERN);
		}
		assertThat(field(requestLines.get(0), "trace.id")).isEqualTo(field(requestLines.get(1), "trace.id"));
	}

	@Test
	void requestIsLoggedAtStartAndEndWithoutIpQueryStringOrHeaders() throws Exception {
		LogCapture capture = LogCapture.start();

		mvc.perform(get("/api/hello").queryParam("email", SECRET_EMAIL)
			.header("X-Api-Secret", "header-secret-value")
			.with((request) -> {
				request.setRemoteAddr("203.0.113.77");
				return request;
			})).andExpect(status().isUnauthorized());

		List<JsonNode> requestLines = requestLines(capture.application(), "/api/hello");
		assertThat(requestLines).hasSize(2);
		JsonNode start = requestLines.get(0);
		assertThat(field(start, "log.level")).isEqualTo("INFO");
		assertThat(field(start, "http.request.method")).isEqualTo("GET");
		JsonNode end = requestLines.get(1);
		assertThat(field(end, "log.level")).isEqualTo("INFO");
		assertThat(field(end, "http.response.status_code")).isEqualTo("401");
		assertThat(field(end, "event.outcome")).isEqualTo("failure");
		assertThat(node(end, "event.duration").isNumber()).isTrue();

		assertThat(String.join("\n", capture.applicationText())).doesNotContain("203.0.113.77")
			.doesNotContain(SECRET_EMAIL)
			.doesNotContain("header-secret-value");
	}

	@Test
	void callerCorrelationIdIsUsedAndControlCharactersAreEscaped() throws Exception {
		LogCapture capture = LogCapture.start();
		mvc.perform(get("/api/csrf").header("X-Correlation-ID", "caller-abc-123")).andExpect(status().isOk());
		assertThat(requestLines(capture.application(), "/api/csrf"))
			.allSatisfy((line) -> assertThat(field(line, "correlation.id")).isEqualTo("caller-abc-123"));

		LogCapture forged = LogCapture.start();
		mvc.perform(get("/api/csrf").header("X-Correlation-ID", "abc\r\n{\"log.level\":\"ERROR\"}"))
			.andExpect(status().isOk());
		List<JsonNode> lines = requestLines(forged.application(), "/api/csrf");
		assertThat(lines).hasSize(2)
			.allSatisfy((line) -> assertThat(field(line, "correlation.id")).doesNotContain("\r", "\n")
				.startsWith("abc\\r\\n"));
		assertThat(forged.application(hasField("log.level", "ERROR"))).isEmpty();
	}

	@Test
	void requestPathWithCrLfCannotForgeALogLine() throws Exception {
		LogCapture capture = LogCapture.start();
		String forgedPath = "/api/hello\r\n{\"message\":\"forged\",\"log\":{\"level\":\"ERROR\"}}";

		mvc.perform(get("/api/hello").with((request) -> {
			request.setRequestURI(forgedPath);
			return request;
		}));

		List<JsonNode> lines = capture.application();
		assertThat(lines).noneMatch(hasField("message", "forged"));
		List<JsonNode> requestLines = lines.stream()
			.filter((line) -> String.valueOf(field(line, "url.path")).startsWith("/api/hello"))
			.toList();
		assertThat(requestLines).hasSize(2)
			.allSatisfy((line) -> assertThat(field(line, "url.path")).doesNotContain("\r", "\n")
				.startsWith("/api/hello\\r\\n"));
	}

	@Test
	void authenticatedRequestLinesCarryTheAccountUuid() throws Exception {
		UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000123");
		LogCapture capture = LogCapture.start();

		mvc.perform(get("/api/hello").with(authentication(new UsernamePasswordAuthenticationToken(
				new TestAccount(accountId), null, AuthorityUtils.createAuthorityList("ROLE_USER")))));

		List<JsonNode> lines = requestLines(capture.application(), "/api/hello");
		assertThat(lines).last().satisfies((end) -> assertThat(field(end, "user.id")).isEqualTo(accountId.toString()));
		assertThat(String.join("\n", capture.applicationText())).doesNotContain("testuser123");
	}

	@Test
	void postWithoutCsrfTokenEmitsWarnAccessControlAuditEvent() throws Exception {
		LogCapture capture = LogCapture.start();

		mvc.perform(post("/api/hello")).andExpect(status().isForbidden());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "access-control"));
		assertThat(events).hasSize(1);
		JsonNode event = events.get(0);
		assertThat(field(event, "log.level")).isEqualTo("WARN");
		assertThat(field(event, "event.outcome")).isEqualTo("failure");
		assertThat(field(event, "event.reason")).isEqualTo("csrf_invalid");
		assertThat(field(event, "url.path")).isEqualTo("/api/hello");
		assertThat(field(event, "http.request.method")).isEqualTo("POST");
		assertThat(field(event, "trace.id")).isNotBlank();
		assertThat(field(event, "service.name")).isEqualTo("secured-hello-world");
		assertThat(capture.application(hasField("event.action", "access-control"))).isEmpty();
	}

	@Test
	void unexpectedExceptionIsLoggedOnceAtErrorWithCategoryAndSanitisedMessage() throws Exception {
		LogCapture capture = LogCapture.start();

		mvc.perform(get("/api/test-logging/failure")).andExpect(status().isInternalServerError());

		List<JsonNode> errors = capture.application(hasField("log.level", "ERROR"));
		assertThat(errors).hasSize(1);
		JsonNode error = errors.get(0);
		assertThat(field(error, "error.code")).isEqualTo("internal_error");
		assertThat(field(error, "error.category")).isEqualTo("application");
		assertThat(field(error, "error.follow_up_action")).isEqualTo("true");
		assertThat(field(error, "error.type")).isEqualTo(IllegalStateException.class.getName());
		assertThat(field(error, "error.message")).doesNotContain(SECRET_EMAIL, "hunter2", "\r", "\n");
		assertThat(field(error, "error.stack_trace")).contains("FailingController").doesNotContain(SECRET_EMAIL, "hunter2");
		assertThat(field(error, "trace.id")).isNotBlank();
		assertThat(node(error, "error_code")).isNull();
	}

	@Test
	void databaseFailureIsCategorisedAsDatabase() throws Exception {
		LogCapture capture = LogCapture.start();

		mvc.perform(get("/api/test-logging/database-failure")).andExpect(status().isInternalServerError());

		assertThat(capture.application(hasField("log.level", "ERROR"))).singleElement()
			.satisfies((error) -> assertThat(field(error, "error.category")).isEqualTo("database"));
	}

	@Test
	void valuesUnderSensitiveKeysAreMasked() {
		LogCapture capture = LogCapture.start();

		LoggerFactory.getLogger(LoggingApiTest.class)
			.atInfo()
			.addKeyValue("password", "hunter2-password")
			.addKeyValue("reset.token", "raw-reset-token")
			.addKeyValue("client_secret", "raw-secret")
			.addKeyValue("csrf", "raw-csrf")
			.addKeyValue("session.id", "raw-session-id")
			.addKeyValue("user.email", SECRET_EMAIL)
			.addKeyValue("username", "testuser123")
			.addKeyValue("session.hash", "abc123")
			.log("Masking check");

		JsonNode line = capture.application(hasField("message", "Masking check")).get(0);
		assertThat(field(line, "password")).isEqualTo("***MASKED***");
		assertThat(field(line, "reset.token")).isEqualTo("***MASKED***");
		assertThat(field(line, "client_secret")).isEqualTo("***MASKED***");
		assertThat(field(line, "csrf")).isEqualTo("***MASKED***");
		assertThat(field(line, "session.id")).isEqualTo("***MASKED***");
		assertThat(field(line, "user.email")).isEqualTo("***MASKED***");
		assertThat(field(line, "username")).isEqualTo("***MASKED***");
		assertThat(field(line, "session.hash")).isEqualTo("abc123");
		assertThat(String.join("\n", capture.applicationText())).doesNotContain("hunter2-password", "raw-reset-token",
				"raw-secret", "raw-csrf", "raw-session-id", SECRET_EMAIL, "testuser123");
	}

	private static List<JsonNode> requestLines(List<JsonNode> lines, String path) {
		Predicate<JsonNode> forPath = hasField("url.path", path);
		return lines.stream().filter(forPath).toList();
	}

	record TestAccount(UUID accountId) implements AccountIdentified {

		@Override
		public String toString() {
			return "testuser123";
		}

	}

	/** Test-only endpoints whose failures carry data that must never reach a log line. */
	@TestConfiguration
	static class FailingEndpoint {

		@Bean
		@Order(0)
		SecurityFilterChain testLoggingChain(HttpSecurity http) throws Exception {
			return http.securityMatcher("/api/test-logging/**")
				.authorizeHttpRequests((auth) -> auth.anyRequest().permitAll())
				.build();
		}

		@Bean
		FailingController loggingFailingController() {
			return new FailingController();
		}

	}

	@RestController
	static class FailingController {

		@GetMapping("/api/test-logging/failure")
		String failure() {
			throw new IllegalStateException(
					"Lookup failed for " + SECRET_EMAIL + " with password=hunter2\r\nFORGED LINE");
		}

		@GetMapping("/api/test-logging/database-failure")
		String databaseFailure() {
			throw new org.springframework.dao.DataAccessResourceFailureException("Connection refused");
		}

	}

}
