package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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

import com.example.securedhello.support.LogCapture;
import com.example.securedhello.support.MutableClock;
import com.example.securedhello.support.RecordingEmailService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Account lockout through the HTTP API, with the Clock seam: 5 consecutive wrong passwords lock an
 * Account for 20 minutes, a Locked Account refuses even the correct password with the same body, and
 * the lock lifts on its own. All test data is synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ MutableClock.Config.class, RecordingEmailService.Config.class })
class LockoutApiTest {

	private static final String PASSWORD = "Synthetic-Pass-42";

	private static final String WRONG_PASSWORD = "Wrong-Pass-4242";

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
		Csrf csrf = csrf();
		mvc.perform(post("/api/register").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(
					Map.of("username", "testuser123", "email", "testuser123@test.example.com", "password", PASSWORD))))
			.andExpect(status().isCreated());
		accountId = jdbc.queryForObject("SELECT id FROM users", UUID.class).toString();
		email.clear();
	}

	@Test
	void fiveWrongPasswordsLockTheAccountAndTheCorrectPasswordGetsTheIdenticalBody() throws Exception {
		String wrongPasswordBody = null;
		for (int attempt = 1; attempt <= 4; attempt++) {
			wrongPasswordBody = body(login(WRONG_PASSWORD).andExpect(status().isUnauthorized()));
		}
		login(WRONG_PASSWORD).andExpect(status().isUnauthorized());

		String lockedBody = body(login(PASSWORD).andExpect(status().isUnauthorized()));

		assertThat(lockedBody).isEqualTo(wrongPasswordBody);
	}

	@Test
	void fourWrongPasswordsDoNotLock() throws Exception {
		for (int attempt = 1; attempt <= 4; attempt++) {
			login(WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}

		login(PASSWORD).andExpect(status().isOk());
	}

	@Test
	void theLockLiftsAfter20MinutesAndASuccessfulLoginResetsTheCounter() throws Exception {
		lockTheAccount();

		clock.advance(Duration.ofMinutes(20).minusSeconds(1));
		login(PASSWORD).andExpect(status().isUnauthorized());
		clock.advance(Duration.ofSeconds(1));
		login(PASSWORD).andExpect(status().isOk());

		// The counter starts again from zero: four more failures do not lock.
		for (int attempt = 1; attempt <= 4; attempt++) {
			login(WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
		login(PASSWORD).andExpect(status().isOk());
	}

	@Test
	void afterAnExpiredLockFiveNewFailuresAreNeededToLockAgain() throws Exception {
		lockTheAccount();
		clock.advance(Duration.ofMinutes(20));

		for (int attempt = 1; attempt <= 4; attempt++) {
			login(WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
		login(PASSWORD).andExpect(status().isOk());
	}

	@Test
	void wrongPasswordsDuringALockDoNotExtendIt() throws Exception {
		lockTheAccount();
		clock.advance(Duration.ofMinutes(19));
		login(WRONG_PASSWORD).andExpect(status().isUnauthorized());

		clock.advance(Duration.ofMinutes(1));

		login(PASSWORD).andExpect(status().isOk());
	}

	@Test
	void theAccountHolderIsNotifiedOnceWhenTheAccountBecomesLocked() throws Exception {
		for (int attempt = 1; attempt <= 4; attempt++) {
			login(WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
		assertThat(email.sent()).isEmpty();

		login(WRONG_PASSWORD).andExpect(status().isUnauthorized());
		login(WRONG_PASSWORD).andExpect(status().isUnauthorized());
		login(PASSWORD).andExpect(status().isUnauthorized());

		assertThat(email.sent())
			.containsExactly(new RecordingEmailService.Sent("account-locked", "testuser123@test.example.com"));
	}

	@Test
	void lockingEmitsOneWarnAccessControlEventWithTheAccountUuid() throws Exception {
		for (int attempt = 1; attempt <= 4; attempt++) {
			login(WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
		LogCapture capture = LogCapture.start();

		login(WRONG_PASSWORD).andExpect(status().isUnauthorized());
		login(WRONG_PASSWORD).andExpect(status().isUnauthorized());

		List<JsonNode> events = capture.awaitAudit(hasField("event.action", "access-control"));
		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(field(event, "log.level")).isEqualTo("WARN");
			assertThat(field(event, "event.outcome")).isEqualTo("failure");
			assertThat(field(event, "event.reason")).isEqualTo("account_locked");
			assertThat(field(event, "user.id")).isEqualTo(accountId);
			assertThat(field(event, "url.path")).isEqualTo("/api/login");
			assertThat(field(event, "trace.id")).isNotBlank();
		});
		assertThat(String.join("\n", capture.auditText())).doesNotContain("testuser123");
		assertThat(String.join("\n", capture.applicationText())).doesNotContain("testuser123");
	}

	private void lockTheAccount() throws Exception {
		for (int attempt = 1; attempt <= 5; attempt++) {
			login(WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
		login(PASSWORD).andExpect(status().isUnauthorized());
	}

	record Csrf(Cookie session, String token) {
	}

	private Csrf csrf() throws Exception {
		MvcResult result = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
		return new Csrf(result.getResponse().getCookie("SESSION"),
				JsonPath.read(result.getResponse().getContentAsString(), "$.token"));
	}

	private ResultActions login(String password) throws Exception {
		Csrf csrf = csrf();
		return mvc.perform(post("/api/login").cookie(csrf.session())
			.header("X-CSRF-TOKEN", csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("username", "testuser123", "password", password))));
	}

	private static String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

}
