package com.example.securedhello.account;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.securedhello.security.SessionControl;
import com.example.securedhello.support.LogCapture;
import com.example.securedhello.support.RecordingEmailService;

import tools.jackson.databind.JsonNode;

/**
 * The post-commit side effects of an Account change (ending Sessions, auditing, notifying) have one
 * owner, {@link AccountEventListener}, and run only once the change has committed. Driven through
 * the services directly, with no controller and no request, which is how the side effects are shown
 * to belong to the change rather than to whichever endpoint made it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RecordingEmailService.Config.class)
class AccountEventsTest {

	@Autowired
	AccountAdministrationService administration;

	@Autowired
	PasswordResetService passwordReset;

	@Autowired
	AccountRepository accounts;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	RecordingEmailService email;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MockMvc mvc;

	@Autowired
	ApplicationEventPublisher events;

	@MockitoSpyBean
	SessionControl sessionControl;

	private UUID admin;

	private UUID target;

	private String targetEmail;

	@BeforeEach
	void anAdminAndATarget() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		admin = account("eventadmin" + suffix, Role.ADMIN);
		targetEmail = "eventtarget" + suffix + "@test.example.com";
		target = accounts
			.save(Account.registered("eventtarget" + suffix, targetEmail, "synthetic-hash", Instant.now()))
			.getId();
		email.clear();
		// The test framework binds a mock request to the thread; a direct service call has none.
		RequestContextHolder.resetRequestAttributes();
		SecurityContextHolder.getContext()
			.setAuthentication(new AccountPrincipal(admin, "synthetic", Role.ADMIN).toAuthentication());
	}

	@AfterEach
	void cleanUp() {
		SecurityContextHolder.clearContext();
		RequestContextHolder.resetRequestAttributes();
		for (UUID id : new UUID[] { admin, target }) {
			jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", id.toString());
			jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", id);
			jdbc.update("DELETE FROM password_history WHERE user_id = ?", id);
			jdbc.update("DELETE FROM users WHERE id = ?", id);
		}
	}

	@Test
	void disablingAnAccountEndsItsSessionsAndIsAuditedWithoutAnyController() {
		storedSessionOf(target);
		LogCapture capture = LogCapture.start();

		administration.setEnabled(admin, target, false);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spring_session WHERE principal_name = ?", Integer.class,
				target.toString())).isZero();
		JsonNode sessionEnd = capture
			.awaitAudit(hasField("event.action", "session-end").and(hasField("user.id", target.toString())))
			.get(0);
		assertThat(field(sessionEnd, "event.reason")).isNotBlank();
		JsonNode change = capture.awaitAudit(hasField("target.user.id", target.toString())).get(0);
		assertThat(field(change, "event.action")).isEqualTo("user-administration");
		assertThat(field(change, "user.id")).isEqualTo(admin.toString());
		// No request, so none of its fields, rather than a failure.
		assertThat(change.has("url")).isFalse();
	}

	@Test
	void aChangeThatRollsBackIsNeitherAuditedNorEndsSessions() {
		storedSessionOf(target);
		LogCapture capture = LogCapture.start();

		transactions.executeWithoutResult((status) -> {
			administration.setEnabled(admin, target, false);
			status.setRollbackOnly();
		});

		assertThat(accounts.findById(target)).hasValueSatisfying((account) -> assertThat(account.isEnabled()).isTrue());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spring_session WHERE principal_name = ?", Integer.class,
				target.toString())).isOne();
		assertThat(capture.audit(hasField("target.user.id", target.toString()))).isEmpty();
		assertThat(capture.audit(hasField("event.action", "session-end").and(hasField("user.id", target.toString()))))
			.isEmpty();
	}

	@Test
	void theResetLinkIsSentAndAuditedOnlyOnceIssuanceCommits() {
		LogCapture capture = LogCapture.start();

		transactions.executeWithoutResult((status) -> {
			passwordReset.issue(targetEmail, "POST", "/api/password-reset/request");
			assertThat(email.sent()).isEmpty();
			assertThat(capture.audit(hasField("user.id", target.toString()))).isEmpty();
		});

		assertThat(email.sent()).containsExactly(new RecordingEmailService.Sent("password-reset-link", targetEmail));
		JsonNode issued = capture.awaitAudit(hasField("user.id", target.toString())).get(0);
		assertThat(field(issued, "event.action")).isEqualTo("password-reset");
		assertThat(field(issued, "url.path")).isEqualTo("/api/password-reset/request");
	}

	@Test
	void aResetIssuanceThatRollsBackSendsNoLink() {
		LogCapture capture = LogCapture.start();

		transactions.executeWithoutResult((status) -> {
			passwordReset.issue(targetEmail, "POST", "/api/password-reset/request");
			status.setRollbackOnly();
		});

		assertThat(email.sent()).isEmpty();
		assertThat(capture.audit(hasField("user.id", target.toString()))).isEmpty();
	}

	/**
	 * A committed disable whose Sessions could not be ended must not answer 2xx: the Admin would be
	 * told the Account is off while its Session is still live. The committed change is still audited.
	 */
	@Test
	void aDisableWhoseSessionsCannotBeEndedIsNotReportedAsASuccess() throws Exception {
		doThrow(new IllegalStateException("synthetic session store failure")).when(sessionControl)
			.endAll(eq(target), anyString(), any(), any());
		LogCapture capture = LogCapture.start();

		int status = mvc
			.perform(patch("/api/admin/users/" + target + "/enabled")
				.with(authentication(new AccountPrincipal(admin, "synthetic", Role.ADMIN).toAuthentication()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"enabled\":false}"))
			.andReturn()
			.getResponse()
			.getStatus();

		assertThat(status).isEqualTo(500);
		JsonNode change = capture.awaitAudit(hasField("target.user.id", target.toString())).get(0);
		assertThat(field(change, "event.action")).isEqualTo("user-administration");
	}

	@Test
	void anAccountEventPublishedOutsideATransactionIsRefusedRatherThanDropped() {
		assertThatIllegalStateException()
			.isThrownBy(() -> events.publishEvent(new AccountEvent.Unlocked(admin, target, true)));
	}

	/**
	 * A bound request without a response (possible off the servlet path) still ends the stored
	 * Sessions, through the no-response path, and the audit still carries the request's fields.
	 */
	@Test
	void aBoundRequestWithoutAResponseStillEndsTheStoredSessions() {
		storedSessionOf(target);
		RequestContextHolder
			.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("PATCH", "/synthetic")));
		LogCapture capture = LogCapture.start();

		administration.setEnabled(admin, target, false);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spring_session WHERE principal_name = ?", Integer.class,
				target.toString())).isZero();
		JsonNode change = capture.awaitAudit(hasField("target.user.id", target.toString())).get(0);
		assertThat(field(change, "url.path")).isEqualTo("/synthetic");
	}

	private UUID account(String username, Role role) {
		Account account = Account.registered(username, username + "@test.example.com", "synthetic-hash", Instant.now());
		account.changeRole(role);
		return accounts.save(account).getId();
	}

	private void storedSessionOf(UUID accountId) {
		long now = Instant.now().toEpochMilli();
		String id = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO spring_session (primary_id, session_id, creation_time, last_access_time, "
				+ "max_inactive_interval, expiry_time, principal_name) VALUES (?, ?, ?, ?, ?, ?, ?)",
				UUID.randomUUID().toString(), id, now, now, 3600, now + 3_600_000L, accountId.toString());
	}

}
