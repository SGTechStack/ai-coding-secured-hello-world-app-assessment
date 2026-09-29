package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static com.example.securedhello.support.LogCapture.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.support.LogCapture;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import tools.jackson.databind.JsonNode;

/**
 * The Audit log module: startup and shutdown events, the audit event contract, and the dedicated
 * audit destination. Startup tests run their own application context, as the bootstrap tests do.
 */
class AuditLogTest {

	private static ConfigurableApplicationContext run(String... args) {
		String[] all = new String[args.length + 2];
		all[0] = "--server.port=0";
		all[1] = "--spring.datasource.url=jdbc:h2:mem:audit-" + System.nanoTime();
		System.arraycopy(args, 0, all, 2, args.length);
		return new SpringApplicationBuilder(BackendApplication.class).profiles("test").run(all);
	}

	@Test
	void startupAndShutdownAreLoggedAndAudited() {
		LogCapture capture = LogCapture.start();

		try (ConfigurableApplicationContext context = run()) {
			JsonNode started = capture.application(hasField("message", "Application started")).get(0);
			assertThat(field(started, "log.level")).isEqualTo("INFO");
			assertThat(field(started, "host.name")).isNotBlank();
			assertThat(field(started, "host.ip")).isNotBlank();
			assertThat(node(started, "spring.profiles.active").toString()).contains("test");
			assertThat(field(started, "service.name")).isEqualTo("secured-hello-world");
			assertThat(field(started, "config.api.base_path")).isEqualTo("/api");
			assertThat(field(started, "config.api.max_request_body_bytes")).isEqualTo("16384");
			assertThat(capture.applicationText()).noneMatch((line) -> line.contains("jdbc:h2"))
				.noneMatch((line) -> line.contains("secured-hello-test"));

			JsonNode startupAudit = capture.awaitAudit(hasField("event.action", "application-startup")).get(0);
			assertThat(field(startupAudit, "log.level")).isEqualTo("INFO");
			assertThat(field(startupAudit, "event.outcome")).isEqualTo("success");
			assertThat(field(startupAudit, "trace.id")).isNotBlank();
			assertThat(capture.application(hasField("event.action", "application-startup"))).isEmpty();
		}

		assertThat(capture.application(hasField("message", "Application stopping"))).hasSize(1);
		JsonNode shutdownAudit = capture.awaitAudit(hasField("event.action", "application-shutdown")).get(0);
		assertThat(field(shutdownAudit, "log.level")).isEqualTo("INFO");
		assertThat(field(shutdownAudit, "trace.id")).isNotBlank();
	}

	@Test
	void auditEventsCarryTheContractFieldsAndGoOnlyToTheAuditFile() {
		UUID actor = UUID.fromString("00000000-0000-0000-0000-00000000000a");
		UUID target = UUID.fromString("00000000-0000-0000-0000-00000000000b");
		MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/admin/users/" + target + "/enabled");
		MockHttpSession session = new MockHttpSession(null, "raw-session-id-value");
		request.setSession(session);

		try (ConfigurableApplicationContext context = run()) {
			AuditLog auditLog = context.getBean(AuditLog.class);
			LogCapture capture = LogCapture.start();

			auditLog.record(AuditEvent.success(AuditAction.USER_ADMINISTRATION)
				.userId(actor)
				.targetUserId(target)
				.change("enabled", false, true)
				.request(request));
			auditLog.record(AuditEvent.failure(AuditAction.USER_AUTHENTICATION, "authentication_failed")
				.passwordAuthentication()
				.sessionHash(session.getId())
				.sourceIpHash("5f2b9a")
				.request(request));
			auditLog.record(AuditEvent.systemFailure(AuditAction.USER_AUTHENTICATION, "database_unavailable"));
			auditLog.record(AuditEvent.success(AuditAction.PASSWORD_RESET).eventType("change"));

			List<JsonNode> events = capture.audit();
			assertThat(events).hasSize(4);

			JsonNode admin = events.get(0);
			assertThat(field(admin, "log.level")).isEqualTo("INFO");
			assertThat(field(admin, "log.logger")).isEqualTo("audit");
			assertThat(field(admin, "event.action")).isEqualTo("user-administration");
			assertThat(field(admin, "event.outcome")).isEqualTo("success");
			assertThat(field(admin, "user.id")).isEqualTo(actor.toString());
			assertThat(field(admin, "target.user.id")).isEqualTo(target.toString());
			assertThat(field(admin, "state.before.enabled")).isEqualTo("false");
			assertThat(field(admin, "state.after.enabled")).isEqualTo("true");
			assertThat(field(admin, "url.path")).isEqualTo("/api/admin/users/" + target + "/enabled");
			assertThat(field(admin, "http.request.method")).isEqualTo("PATCH");
			assertThat(field(admin, "trace.id")).isNotBlank();

			JsonNode failedLogin = events.get(1);
			assertThat(field(failedLogin, "log.level")).isEqualTo("WARN");
			assertThat(field(failedLogin, "event.outcome")).isEqualTo("failure");
			assertThat(field(failedLogin, "event.reason")).isEqualTo("authentication_failed");
			assertThat(field(failedLogin, "authentication.method")).isEqualTo("password");
			assertThat(field(failedLogin, "session.hash")).matches("[0-9a-f]{64}");
			assertThat(field(failedLogin, "source.ip_hash")).isEqualTo("5f2b9a");
			assertThat(node(failedLogin, "user.id")).isNull();

			JsonNode systemFailure = events.get(2);
			assertThat(field(systemFailure, "log.level")).isEqualTo("ERROR");
			assertThat(field(systemFailure, "event.outcome")).isEqualTo("failure");

			assertThat(node(events.get(3), "event.type").toString()).isEqualTo("[\"change\"]");

			assertThat(String.join("\n", capture.auditText())).doesNotContain("raw-session-id-value");
			assertThat(capture.application(hasField("log.logger", "audit"))).isEmpty();
		}
	}

	@Test
	void auditFileIsSeparateAndKeepsAtLeast90DaysOfHistory() {
		try (ConfigurableApplicationContext context = run()) {
			Logger audit = (Logger) LoggerFactory.getLogger(LogCapture.AUDIT_LOGGER);
			assertThat(audit.isAdditive()).isFalse();
			RollingFileAppender<?> appender = (RollingFileAppender<?>) audit.getAppender(LogCapture.AUDIT_APPENDER);
			assertThat(((TimeBasedRollingPolicy<?>) appender.getRollingPolicy()).getMaxHistory())
				.isGreaterThanOrEqualTo(90);
			assertThat(LogCapture.auditLogFile()).isNotEqualTo(LogCapture.applicationLogFile());
		}
	}

	@Test
	void startupFailsWhenAuditHistoryIsUnder90Days() {
		assertThatThrownBy(() -> run("--app.logging.audit-max-history-days=30").close())
			.hasStackTraceContaining("app.logging.audit-max-history-days");
	}

	@Test
	void sqlLoggingIsOffOutsideDev() {
		try (ConfigurableApplicationContext context = run()) {
			assertThat(context.getEnvironment().getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
			assertThat(LoggerFactory.getLogger("org.hibernate.SQL").isDebugEnabled()).isFalse();
		}
	}

}
