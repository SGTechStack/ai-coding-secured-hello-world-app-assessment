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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.example.securedhello.support.LogCapture;
import com.example.securedhello.support.MutableClock;
import com.example.securedhello.support.RecordingEmailService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Self-service password reset through {@code POST /api/password-reset/request} and
 * {@code POST /api/password-reset/confirm}, like the SPA. Issuance and its email run on a background
 * executor (spec), so every issuance-dependent assertion waits for the recorded email with a bounded
 * timeout (Seam 3), and the token itself comes from the recorded link's {@code #token=} fragment. All
 * test data is synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ MutableClock.Config.class, RecordingEmailService.Config.class })
class PasswordResetApiTest {

	private static final String USERNAME = "testuser123";

	private static final String EMAIL = "testuser123@test.example.com";

	private static final String PASSWORD = "Synthetic-Pass-42";

	private static final String NEW_PASSWORD = "Synthetic-Pass-43";

	private static final String SECOND_USERNAME = "testuser456";

	private static final String SECOND_EMAIL = "testuser456@test.example.com";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MutableClock clock;

	@Autowired
	RecordingEmailService email;

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
			.content(JSON.writeValueAsString(Map.of("username", USERNAME, "email", EMAIL, "password", PASSWORD))))
			.andExpect(status().isCreated());
		accountId = jdbc.queryForObject("SELECT id FROM users", UUID.class).toString();
		email.clear();
	}

	@Test
	void theRequestReturns202WithAGenericMessageBeforeAnyLookup() throws Exception {
		// Deterministic, not timing-based: the background task blocks right at "record the email", so
		// if the response already came back, nothing has been recorded yet, whatever the scheduler did.
		email.blockNextResetLink();

		requestReset(EMAIL).andExpect(status().isAccepted())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.message").isNotEmpty());
		assertThat(email.sent()).isEmpty();

		email.releaseResetLink();
		String token = email.awaitResetLink();
		assertThat(token).isNotBlank();
	}

	@Test
	void anUnregisteredEmailGetsTheIdenticalResponseAndNoToken() throws Exception {
		requestReset("unregistered@test.example.com").andExpect(status().isAccepted())
			.andExpect(jsonPath("$.message").isNotEmpty());

		// Nothing to wait for: assert after a request that *would* issue, to bound the wait.
		requestReset(EMAIL).andExpect(status().isAccepted());
		email.awaitResetLink();

		assertThat(email.sent()).hasSize(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class)).isEqualTo(1);
	}

	@Test
	void aDisabledAccountGetsNoTokenAndNoEmailWithTheIdenticalResponse() throws Exception {
		jdbc.update("UPDATE users SET enabled = false WHERE username = ?", USERNAME);

		requestReset(EMAIL).andExpect(status().isAccepted()).andExpect(jsonPath("$.message").isNotEmpty());
		// Bound the wait: register a second, enabled Account and wait for its own reset email; the
		// Disabled Account's earlier, much cheaper (no-op) task has long finished by the time this
		// one, which actually issues and sends, has been recorded.
		registerSecondAccount();
		requestReset(SECOND_EMAIL).andExpect(status().isAccepted());
		email.awaitResetLink();

		assertThat(email.sent()).containsExactly(new RecordingEmailService.Sent("password-reset-link", SECOND_EMAIL));
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = (SELECT id FROM users WHERE username = ?)",
				Integer.class, USERNAME))
			.isEqualTo(0);
	}

	private void registerSecondAccount() throws Exception {
		Csrf csrf = csrf(null);
		mvc.perform(post("/api/register").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("username", SECOND_USERNAME, "email", SECOND_EMAIL, "password", PASSWORD))))
			.andExpect(status().isCreated());
	}

	@Test
	void aTokenWorksOnceAndTheHolderIsLoggedInWithTheNewPassword() throws Exception {
		String token = issueToken();

		confirm(token, NEW_PASSWORD).andExpect(status().isOk());

		login(NEW_PASSWORD).andExpect(status().isOk());
		confirm(token, "Synthetic-Pass-44").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("token_invalid"))
			.andExpect(jsonPath("$.detail").value("password reset token expired or invalid"));
	}

	@Test
	void anExpiredTokenIsRefused() throws Exception {
		String token = issueToken();

		clock.advance(Duration.ofMinutes(30));

		confirm(token, NEW_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("token_invalid"));
	}

	@Test
	void aTokenStillWithinThirtyMinutesWorks() throws Exception {
		String token = issueToken();

		clock.advance(Duration.ofMinutes(29));

		confirm(token, NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void anUnknownTokenIsRefused() throws Exception {
		confirm("unknown-synthetic-token-value", NEW_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("token_invalid"))
			.andExpect(jsonPath("$.detail").value("password reset token expired or invalid"));
	}

	@Test
	void aNewRequestCancelsTheOlderToken() throws Exception {
		String firstToken = issueToken();
		clock.advance(Duration.ofSeconds(1));
		requestReset(EMAIL).andExpect(status().isAccepted());
		List<String> links = email.awaitResetLinks(2);
		String secondToken = RecordingEmailService.tokenFrom(links.get(1));

		confirm(firstToken, NEW_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("token_invalid"));
		confirm(secondToken, NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void aPasswordChangeCancelsAPendingToken() throws Exception {
		String token = issueToken();
		Cookie session = loggedIn(PASSWORD);

		change(session, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

		confirm(token, "Synthetic-Pass-44").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("token_invalid"));
	}

	@Test
	void aSuccessfulResetEndsEveryExistingSession() throws Exception {
		Cookie session = loggedIn(PASSWORD);
		String token = issueToken();

		confirm(token, NEW_PASSWORD).andExpect(status().isOk());

		me(session).andExpect(status().isUnauthorized());
		login(PASSWORD).andExpect(status().isUnauthorized());
		login(NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void aResetLeavesAnActiveLockInPlace() throws Exception {
		clock.advance(Duration.ofSeconds(1));
		jdbc.update("UPDATE users SET locked_until = ? WHERE username = ?",
				Timestamp.from(clock.instant().plus(Duration.ofMinutes(20))), USERNAME);
		String token = issueToken();

		confirm(token, NEW_PASSWORD).andExpect(status().isOk());

		login(NEW_PASSWORD).andExpect(status().isUnauthorized());
		clock.advance(Duration.ofMinutes(20));
		login(NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void theCompletionNotificationIsSentAfterSuccess() throws Exception {
		String token = issueToken();
		email.clear();

		confirm(token, NEW_PASSWORD).andExpect(status().isOk());

		assertThat(email.sent()).containsExactly(new RecordingEmailService.Sent("password-reset-completed", EMAIL));
	}

	@Test
	void eachPasswordRuleFailureReturnsPasswordPolicy() throws Exception {
		String token = issueToken();

		confirm(token, "weak").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("password_policy"))
			.andExpect(jsonPath("$.violations").isNotEmpty());

		// The token is not consumed by a failed confirmation.
		confirm(token, NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void reusingTheCurrentPasswordOnResetIsRefusedWithPasswordHistory() throws Exception {
		String token = issueToken();

		confirm(token, PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("password_history"));

		confirm(token, NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void theRequestIsRejectedWithoutTheCsrfToken() throws Exception {
		mvc
			.perform(post("/api/password-reset/request").contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(Map.of("email", EMAIL))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("csrf_invalid"));
	}

	@Test
	void theConfirmationIsRejectedWithoutTheCsrfToken() throws Exception {
		mvc
			.perform(post("/api/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
				.content(JSON.writeValueAsString(Map.of("token", "any-token", "newPassword", NEW_PASSWORD))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("csrf_invalid"));
	}

	@Test
	void theRequestRateLimitByEmailIsEnforced() throws Exception {
		for (int n = 1; n <= 3; n++) {
			requestReset(EMAIL).andExpect(status().isAccepted());
		}

		requestReset(EMAIL).andExpect(status().isTooManyRequests())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.code").value("too_many_requests"))
			.andExpect(jsonPath("$.detail").value("too many requests"));
	}

	@Test
	void theRequestRateLimitByIpIsEnforced() throws Exception {
		String address = "192.0.2.40";
		// Ten different emails, so the per-email limit (3/hour) never fires before the per-IP one (10/hour).
		for (int n = 1; n <= 10; n++) {
			requestReset("testuser" + n + "@test.example.com", address).andExpect(status().isAccepted());
		}

		requestReset("testuser11@test.example.com", address).andExpect(status().isTooManyRequests())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.code").value("too_many_requests"));
		// A different address is unaffected.
		requestReset("testuser12@test.example.com", "192.0.2.41").andExpect(status().isAccepted());
	}

	@Test
	void theConfirmationRateLimitIsEnforced() throws Exception {
		for (int n = 1; n <= 10; n++) {
			confirm("unknown-token-" + n, NEW_PASSWORD).andExpect(status().isBadRequest());
		}
		LogCapture capture = LogCapture.start();

		confirm("unknown-token-11", NEW_PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.code").value("too_many_requests"));

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "access-control"));
		assertThat(events).singleElement()
			.satisfies((event) -> assertThat(field(event, "event.reason")).isEqualTo("rate_limited"));
	}

	@Test
	void issuanceIsAuditedAsAnInfoPasswordResetEvent() throws Exception {
		LogCapture capture = LogCapture.start();

		requestReset(EMAIL).andExpect(status().isAccepted());
		email.awaitResetLink();

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "password-reset"));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("INFO");
			assertThat(field(event, "event.outcome")).isEqualTo("success");
			assertThat(field(event, "user.id")).isEqualTo(accountId);
			assertThat(field(event, "url.path")).isEqualTo("/api/password-reset/request");
			assertThat(field(event, "trace.id")).isNotBlank();
		});
		assertNoTokenLogged(capture);
	}

	@Test
	void completionIsAuditedAsAnInfoPasswordResetEventAndSessionEnd() throws Exception {
		loggedIn(PASSWORD);
		String token = issueToken();
		LogCapture capture = LogCapture.start();

		confirm(token, NEW_PASSWORD).andExpect(status().isOk());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "password-reset"));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("INFO");
			assertThat(field(event, "event.outcome")).isEqualTo("success");
			assertThat(field(event, "user.id")).isEqualTo(accountId);
			assertThat(field(event, "url.path")).isEqualTo("/api/password-reset/confirm");
		});
		assertThat(capture.audit(hasField("event.action", "session-end"))).singleElement()
			.satisfies((event) -> assertThat(field(event, "event.reason")).isEqualTo("password_reset"));
		assertNoTokenLogged(capture);
	}

	@Test
	void anUnknownTokenFailureIsAuditedWithoutAnAccountId() throws Exception {
		LogCapture capture = LogCapture.start();

		confirm("unknown-synthetic-token-value", NEW_PASSWORD).andExpect(status().isBadRequest());

		List<JsonNode> events = capture.awaitAudit(
				(node) -> "password-reset".equals(field(node, "event.action")) && "failure".equals(field(node, "event.outcome")));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("WARN");
			assertThat(field(event, "event.reason")).isEqualTo("token_invalid");
			assertThat(field(event, "user.id")).isNull();
		});
		assertNoTokenLogged(capture);
	}

	private void assertNoTokenLogged(LogCapture capture) throws Exception {
		List<String> lines = new ArrayList<>();
		lines.addAll(capture.applicationText());
		lines.addAll(capture.auditText());
		for (String link : email.resetLinks()) {
			String token = RecordingEmailService.tokenFrom(link);
			String tokenHash = sha256Hex(token);
			assertThat(lines).noneMatch((line) -> line.contains(token));
			assertThat(lines).noneMatch((line) -> line.contains(tokenHash));
		}
		assertThat(lines).noneMatch((line) -> line.contains(EMAIL)).noneMatch((line) -> line.contains(USERNAME));
	}

	/** The same hex-encoded SHA-256 the service stores as {@code token_hash}, so no test hard-codes it. */
	private static String sha256Hex(String token) throws Exception {
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(digest);
	}

	/** Issues one Reset Token through the API and returns it, waiting for the background email. */
	private String issueToken() throws Exception {
		requestReset(EMAIL).andExpect(status().isAccepted());
		return RecordingEmailService.tokenFrom(email.awaitResetLink());
	}

	private ResultActions requestReset(String targetEmail) throws Exception {
		return requestReset(targetEmail, null);
	}

	private ResultActions requestReset(String targetEmail, String address) throws Exception {
		Csrf csrf = csrf(null);
		var request = post("/api/password-reset/request").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("email", targetEmail)));
		return mvc.perform((address != null) ? from(address, request) : request);
	}

	private static MockHttpServletRequestBuilder from(String address, MockHttpServletRequestBuilder request) {
		return request.with((servletRequest) -> {
			servletRequest.setRemoteAddr(address);
			return servletRequest;
		});
	}

	private ResultActions confirm(String token, String newPassword) throws Exception {
		Csrf csrf = csrf(null);
		return mvc.perform(post("/api/password-reset/confirm").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("token", token, "newPassword", newPassword))));
	}

	private ResultActions change(Cookie session, String currentPassword, String newPassword) throws Exception {
		Csrf csrf = csrf(session);
		return mvc.perform(MockMvcRequestBuilders.patch("/api/me/password")
			.cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("currentPassword", currentPassword, "newPassword", newPassword))));
	}

	record Csrf(Cookie session, String token) {
	}

	private Csrf csrf(Cookie session) throws Exception {
		MvcResult result = mvc.perform((session != null) ? get("/api/csrf").cookie(session) : get("/api/csrf"))
			.andExpect(status().isOk())
			.andReturn();
		Cookie issued = result.getResponse().getCookie("SESSION");
		String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
		return new Csrf((issued != null) ? issued : session, token);
	}

	private ResultActions login(String password) throws Exception {
		Csrf csrf = csrf(null);
		return mvc.perform(post("/api/login").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("username", USERNAME, "password", password))));
	}

	private Cookie loggedIn(String password) throws Exception {
		return login(password).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
	}

	private ResultActions me(Cookie session) throws Exception {
		return mvc.perform(get("/api/me").cookie(session));
	}

}
