package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static com.example.securedhello.support.LogCapture.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * Password Change through {@code PATCH /api/me/password}, like the SPA: the current password is
 * required, the new one is checked against the Credential policy and the Password History (last 3,
 * current included), and success ends every Session of the Account and notifies its holder. Two
 * Sessions per Account are allowed here so that "every Session" covers another one too. All test
 * data is synthetic. A wrong current password counts toward lockout exactly like a failed login.
 */
@SpringBootTest(properties = "app.session.max-concurrent-per-account=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ MutableClock.Config.class, RecordingEmailService.Config.class })
class PasswordChangeApiTest {

	private static final String PASSWORD = "Synthetic-Pass-42";

	private static final String SECOND = "Synthetic-Pass-43";

	private static final String THIRD = "Synthetic-Pass-44";

	private static final String FOURTH = "Synthetic-Pass-45";

	private static final String WRONG = "Wrong-Pass-4242";

	private static final String EMAIL = "testuser123@test.example.com";

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
		jdbc.update("DELETE FROM deleted_users");
		jdbc.update("DELETE FROM users");
		jdbc.update("DELETE FROM spring_session");
		Csrf csrf = csrf(null);
		mvc.perform(post("/api/register").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("username", "testuser123", "email", EMAIL, "password", PASSWORD))))
			.andExpect(status().isCreated());
		accountId = jdbc.queryForObject("SELECT id FROM users", UUID.class).toString();
		email.clear();
	}

	@AfterEach
	void deliveriesWorkAgain() {
		email.clear();
	}

	@Test
	void successReturns200AndEndsTheCurrentAndEveryOtherSession() throws Exception {
		Cookie other = loggedIn(PASSWORD);
		Cookie current = loggedIn(PASSWORD);
		me(other).andExpect(status().isOk());

		change(current, PASSWORD, SECOND).andExpect(status().isOk());

		me(current).andExpect(status().isUnauthorized());
		me(other).andExpect(status().isUnauthorized());
		login(PASSWORD).andExpect(status().isUnauthorized());
		login(SECOND).andExpect(status().isOk());
	}

	@Test
	void aWrongCurrentPasswordIsRefusedAndNothingChanges() throws Exception {
		Cookie session = loggedIn(PASSWORD);

		change(session, "Wrong-Pass-4242", SECOND).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("current_password_invalid"))
			.andExpect(jsonPath("$.violations").doesNotExist());

		me(session).andExpect(status().isOk());
		assertThat(email.sent()).isEmpty();
		login(SECOND).andExpect(status().isUnauthorized());
		login(PASSWORD).andExpect(status().isOk());
	}

	@ParameterizedTest
	@CsvSource({ "Short-Pw-42, min_length", "lowercase-only-password-1, uppercase",
			"UPPERCASE-ONLY-PASSWORD-1, lowercase", "No-Digits-In-This-Password, digit",
			"NoSpecialCharacters42, special", "Password1234!, common_password",
			"Aa1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa, max_length",
			"Aa1-éééééééééééééééééééééééééééééééééééé, max_bytes" })
	void eachPolicyFailureIsRefusedWithItsViolation(String newPassword, String violation) throws Exception {
		Cookie session = loggedIn(PASSWORD);

		change(session, PASSWORD, newPassword).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("password_policy"))
			.andExpect(jsonPath("$.violations", contains(violation)));

		me(session).andExpect(status().isOk());
		assertThat(email.sent()).isEmpty();
	}

	@Test
	void reusingTheCurrentPasswordIsRefused() throws Exception {
		Cookie session = loggedIn(PASSWORD);

		change(session, PASSWORD, PASSWORD).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("password_history"));

		me(session).andExpect(status().isOk());
	}

	@Test
	void reusingAPreviousPasswordWithinTheLastThreeIsRefused() throws Exception {
		changeSuccessfully(PASSWORD, SECOND);
		changeSuccessfully(SECOND, THIRD);

		change(loggedIn(THIRD), THIRD, PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("password_history"));
		change(loggedIn(THIRD), THIRD, SECOND).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("password_history"));
	}

	@Test
	void theHistoryKeepsOnlyTheLastThreePasswordsSoAnOlderOneMayBeReused() throws Exception {
		changeSuccessfully(PASSWORD, SECOND);
		changeSuccessfully(SECOND, THIRD);
		changeSuccessfully(THIRD, FOURTH);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_history WHERE user_id = ?", Integer.class,
				UUID.fromString(accountId)))
			.isEqualTo(3);
		changeSuccessfully(FOURTH, PASSWORD);
	}

	@Test
	void theAccountHolderIsNotifiedOfTheChange() throws Exception {
		changeSuccessfully(PASSWORD, SECOND);

		assertThat(email.sent()).containsExactly(new RecordingEmailService.Sent("password-changed", EMAIL));
	}

	/** Notification is fire-and-forget: a committed change answers 200 even when its email fails. */
	@Test
	void aChangeWhoseEmailCannotBeSentStillSucceeds() throws Exception {
		Cookie session = loggedIn(PASSWORD);
		email.failDeliveries();
		LogCapture capture = LogCapture.start();

		change(session, PASSWORD, SECOND).andExpect(status().isOk());

		me(session).andExpect(status().isUnauthorized());
		assertThat(capture.application(hasField("error.code", "notification_failed"))).singleElement()
			.satisfies((event) -> assertThat(field(event, "log.level")).isEqualTo("ERROR"));
		login(SECOND).andExpect(status().isOk());
	}

	/** A lock email that cannot be sent leaves the locking attempt's 400 and its audit events unchanged. */
	@Test
	void aLockEmailThatCannotBeSentStillAnswersCurrentPasswordInvalidAndAuditsTheLock() throws Exception {
		Cookie session = loggedIn(PASSWORD);
		for (int attempt = 1; attempt <= 4; attempt++) {
			change(session, WRONG, SECOND).andExpect(status().isBadRequest());
		}
		email.failDeliveries();
		LogCapture capture = LogCapture.start();

		change(session, WRONG, SECOND).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("current_password_invalid"));

		assertThat(capture.awaitAudit(hasField("event.action", "access-control"))).singleElement()
			.satisfies((event) -> {
				assertThat(field(event, "event.reason")).isEqualTo("account_locked");
				assertThat(field(event, "user.id")).isEqualTo(accountId);
			});
		assertThat(capture.audit(hasField("event.action", "password-reset"))).singleElement()
			.satisfies((event) -> assertThat(field(event, "event.reason")).isEqualTo("current_password_invalid"));
		login(PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void fiveWrongCurrentPasswordsLockTheAccountLikeFailedLogins() throws Exception {
		Cookie session = loggedIn(PASSWORD);
		for (int attempt = 1; attempt <= 4; attempt++) {
			change(session, WRONG, SECOND).andExpect(status().isBadRequest());
		}
		assertThat(email.sent()).isEmpty();
		LogCapture capture = LogCapture.start();

		change(session, WRONG, SECOND).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("current_password_invalid"));

		assertThat(email.sent()).containsExactly(new RecordingEmailService.Sent("account-locked", EMAIL));
		login(PASSWORD).andExpect(status().isUnauthorized());
		List<JsonNode> locks = capture.awaitAudit(hasField("event.action", "access-control"));
		assertThat(locks).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("WARN");
			assertThat(field(event, "event.reason")).isEqualTo("account_locked");
			assertThat(field(event, "user.id")).isEqualTo(accountId);
			assertThat(field(event, "url.path")).isEqualTo("/api/me/password");
		});
		assertThat(capture.audit(hasField("event.action", "password-reset"))).singleElement()
			.satisfies((event) -> assertThat(field(event, "event.reason")).isEqualTo("current_password_invalid"));
		assertNoSecretsLogged(capture);
	}

	@Test
	void aLockedAccountRefusesEvenTheCorrectCurrentPasswordWithTheIdenticalBodyUntilTheLockLifts()
			throws Exception {
		Cookie session = loggedIn(PASSWORD);
		String wrongBody = null;
		for (int attempt = 1; attempt <= 5; attempt++) {
			wrongBody = change(session, WRONG, SECOND).andExpect(status().isBadRequest())
				.andReturn()
				.getResponse()
				.getContentAsString();
		}

		String lockedBody = change(session, PASSWORD, SECOND).andExpect(status().isBadRequest())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(lockedBody).isEqualTo(wrongBody);
		me(session).andExpect(status().isOk());
		assertThat(email.sent()).extracting(RecordingEmailService.Sent::operation).doesNotContain("password-changed");
		clock.advance(Duration.ofMinutes(20));
		login(SECOND).andExpect(status().isUnauthorized());
		change(loggedIn(PASSWORD), PASSWORD, SECOND).andExpect(status().isOk());
		login(SECOND).andExpect(status().isOk());
	}

	@Test
	void wrongCurrentPasswordsAndWrongLoginsShareOneCounter() throws Exception {
		Cookie session = loggedIn(PASSWORD);
		for (int attempt = 1; attempt <= 4; attempt++) {
			change(session, WRONG, SECOND).andExpect(status().isBadRequest());
		}

		login(WRONG).andExpect(status().isUnauthorized());

		assertThat(email.sent()).containsExactly(new RecordingEmailService.Sent("account-locked", EMAIL));
		login(PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void aChangeBelowTheThresholdSucceedsAndTheNextLoginStartsTheCountAgain() throws Exception {
		Cookie session = loggedIn(PASSWORD);
		for (int attempt = 1; attempt <= 4; attempt++) {
			change(session, WRONG, SECOND).andExpect(status().isBadRequest());
		}

		change(session, PASSWORD, SECOND).andExpect(status().isOk());
		Cookie next = loggedIn(SECOND);
		for (int attempt = 1; attempt <= 4; attempt++) {
			change(next, WRONG, THIRD).andExpect(status().isBadRequest());
		}

		me(next).andExpect(status().isOk());
		assertThat(email.sent()).containsExactly(new RecordingEmailService.Sent("password-changed", EMAIL));
		login(SECOND).andExpect(status().isOk());
	}

	@Test
	void successIsAuditedAsAnInfoPasswordResetChangeWithEverySessionEnd() throws Exception {
		Cookie session = loggedIn(PASSWORD);
		LogCapture capture = LogCapture.start();

		change(session, PASSWORD, SECOND).andExpect(status().isOk());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "password-reset"));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("INFO");
			assertThat(field(event, "event.outcome")).isEqualTo("success");
			assertThat(field(event, "event.type")).isEqualTo("[\"change\"]");
			assertThat(field(event, "user.id")).isEqualTo(accountId);
			assertThat(field(event, "url.path")).isEqualTo("/api/me/password");
			assertThat(field(event, "trace.id")).isNotBlank();
		});
		assertThat(capture.audit(hasField("event.action", "session-end"))).singleElement()
			.satisfies((event) -> assertThat(field(event, "event.reason")).isEqualTo("password_change"));
		assertNoSecretsLogged(capture);
	}

	@ParameterizedTest
	@CsvSource({ "Wrong-Pass-4242, Synthetic-Pass-43, current_password_invalid",
			"Synthetic-Pass-42, weak, password_policy", "Synthetic-Pass-42, Synthetic-Pass-42, password_history" })
	void eachFailureIsAuditedAsAWarnPasswordResetChangeWithAGenericReason(String currentPassword,
			String newPassword, String reason) throws Exception {
		Cookie session = loggedIn(PASSWORD);
		LogCapture capture = LogCapture.start();

		change(session, currentPassword, newPassword).andExpect(status().isBadRequest());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "password-reset"));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("WARN");
			assertThat(field(event, "event.outcome")).isEqualTo("failure");
			assertThat(field(event, "event.reason")).isEqualTo(reason);
			assertThat(field(event, "event.type")).isEqualTo("[\"change\"]");
			assertThat(field(event, "user.id")).isEqualTo(accountId);
			assertThat(node(event, "violations")).isNull();
		});
		assertThat(capture.audit(hasField("event.action", "session-end"))).isEmpty();
		assertNoSecretsLogged(capture);
	}

	@Test
	void aVisitorCannotChangeAPassword() throws Exception {
		Csrf csrf = csrf(null);
		mvc.perform(patch("/api/me/password").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(PASSWORD, SECOND))).andExpect(status().isUnauthorized());
	}

	@Test
	void aChangeWithoutTheCsrfTokenIsRejected() throws Exception {
		Cookie session = loggedIn(PASSWORD);

		mvc.perform(patch("/api/me/password").cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(PASSWORD, SECOND))).andExpect(status().isForbidden());

		login(SECOND).andExpect(status().isUnauthorized());
	}

	@Test
	void missingFieldsAreAValidationError() throws Exception {
		Cookie session = loggedIn(PASSWORD);
		Csrf csrf = csrf(session);

		mvc.perform(patch("/api/me/password").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation"));
	}

	private void changeSuccessfully(String currentPassword, String newPassword) throws Exception {
		// Password History is ordered by time; changes are never at the same instant in practice.
		clock.advance(Duration.ofSeconds(1));
		change(loggedIn(currentPassword), currentPassword, newPassword).andExpect(status().isOk());
	}

	private static void assertNoSecretsLogged(LogCapture capture) {
		String logs = String.join("\n", capture.auditText()) + "\n" + String.join("\n", capture.applicationText());
		assertThat(logs).doesNotContain("testuser123")
			.doesNotContain(PASSWORD)
			.doesNotContain(SECOND)
			.doesNotContain("Wrong-Pass-4242");
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
			.content(JSON.writeValueAsString(Map.of("username", "testuser123", "password", password))));
	}

	private Cookie loggedIn(String password) throws Exception {
		return login(password).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
	}

	private ResultActions change(Cookie session, String currentPassword, String newPassword) throws Exception {
		Csrf csrf = csrf(session);
		return mvc.perform(patch("/api/me/password").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(currentPassword, newPassword)));
	}

	private static String body(String currentPassword, String newPassword) {
		return JSON.writeValueAsString(Map.of("currentPassword", currentPassword, "newPassword", newPassword));
	}

	private ResultActions me(Cookie session) throws Exception {
		return mvc.perform(get("/api/me").cookie(session));
	}

}
