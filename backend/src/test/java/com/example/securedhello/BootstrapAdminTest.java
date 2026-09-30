package com.example.securedhello;

import static com.example.securedhello.support.LogCapture.field;
import static com.example.securedhello.support.LogCapture.hasField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.example.securedhello.support.LogCapture;

import tools.jackson.databind.JsonNode;

/**
 * Bootstrap tests for the Bootstrap Admin initializer: separate application contexts per profile and
 * property set. A database named per test (kept open between contexts) stands for a restart. All
 * credentials are synthetic.
 */
class BootstrapAdminTest {

	private static final String USERNAME = "--app.bootstrap-admin.username=BootAdmin";

	private static final String PASSWORD_VALUE = "Synthetic-Boot-Pass-42";

	private static final String PASSWORD = "--app.bootstrap-admin.password=" + PASSWORD_VALUE;

	private static final String EMAIL = "--app.bootstrap-admin.email=BootAdmin@test.example.com";

	private static final String[] PROD = { "--app.cors.allowed-origins=https://app.example.invalid",
			"--app.ip-hash.key=synthetic-startup-test-key" };

	/** Runs the app on the named in-memory database; args override every properties file. */
	private static ConfigurableApplicationContext run(String database, String profile, String... args) {
		List<String> all = new ArrayList<>(List.of("--server.port=0",
				"--spring.datasource.url=jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1"));
		all.addAll(Arrays.asList(args));
		return new SpringApplicationBuilder(BackendApplication.class).profiles(profile).run(all.toArray(String[]::new));
	}

	private static String database() {
		return "bootstrap-" + System.nanoTime();
	}

	private static List<Map<String, Object>> admins(ConfigurableApplicationContext context) {
		return context.getBean(JdbcTemplate.class).queryForList("SELECT * FROM users WHERE role = 'ADMIN'");
	}

	@Test
	void createsTheBootstrapAdminOnceWithTheConfiguredEmailAndARequiredPasswordChange() {
		LogCapture capture = LogCapture.start();

		try (ConfigurableApplicationContext context = run(database(), "test", USERNAME, PASSWORD, EMAIL)) {
			assertThat(admins(context)).singleElement().satisfies((admin) -> {
				assertThat(admin.get("username")).isEqualTo("bootadmin");
				assertThat(admin.get("email")).isEqualTo("bootadmin@test.example.com");
				assertThat(admin.get("enabled")).isEqualTo(true);
				assertThat(admin.get("password_change_required")).isEqualTo(true);
				String hash = (String) admin.get("password_hash");
				assertThat(hash).startsWith("$2a$12$");
				assertThat(new BCryptPasswordEncoder().matches(PASSWORD_VALUE, hash)).isTrue();
			});
			String id = admins(context).get(0).get("id").toString();
			assertThat(context.getBean(JdbcTemplate.class)
				.queryForObject("SELECT COUNT(*) FROM password_history WHERE user_id = ?", Integer.class,
						admins(context).get(0).get("id")))
				.isEqualTo(1);

			List<JsonNode> events = capture.awaitAudit(hasField("event.action", "user-provisioning"));
			assertThat(events).singleElement().satisfies((event) -> {
				assertThat(field(event, "log.level")).isEqualTo("INFO");
				assertThat(field(event, "event.outcome")).isEqualTo("success");
				assertThat(field(event, "event.reason")).isEqualTo("bootstrap_admin");
				assertThat(field(event, "user.id")).isEqualTo(id);
				assertThat(field(event, "trace.id")).isNotBlank();
			});
		}
		assertThat(capture.auditText()).noneMatch((line) -> line.contains(PASSWORD_VALUE));
		assertThat(capture.applicationText()).noneMatch((line) -> line.contains(PASSWORD_VALUE));
	}

	@Test
	void restartingWhileAnAdminExistsCreatesNothing() {
		String database = database();
		try (ConfigurableApplicationContext context = run(database, "test", USERNAME, PASSWORD, EMAIL)) {
			assertThat(admins(context)).hasSize(1);
		}
		LogCapture capture = LogCapture.start();

		try (ConfigurableApplicationContext context = run(database, "test", "--app.bootstrap-admin.username=otheradmin",
				PASSWORD, "--app.bootstrap-admin.email=otheradmin@test.example.com")) {
			assertThat(admins(context)).singleElement()
				.satisfies((admin) -> assertThat(admin.get("username")).isEqualTo("bootadmin"));
		}
		assertThat(capture.audit(hasField("event.action", "user-provisioning"))).isEmpty();
	}

	@Test
	void aDisabledAdminStillCountsSoRestartingCreatesNothing() {
		String database = database();
		try (ConfigurableApplicationContext context = run(database, "test", USERNAME, PASSWORD, EMAIL)) {
			context.getBean(JdbcTemplate.class).update("UPDATE users SET enabled = FALSE");
		}

		try (ConfigurableApplicationContext context = run(database, "test", "--app.bootstrap-admin.username=otheradmin",
				PASSWORD, "--app.bootstrap-admin.email=otheradmin@test.example.com")) {
			assertThat(admins(context)).singleElement()
				.satisfies((admin) -> assertThat(admin.get("enabled")).isEqualTo(false));
		}
	}

	@Test
	void devFallsBackToTheLocalDefaultsSkippingThePolicyWithALoudWarning() {
		LogCapture capture = LogCapture.start();

		try (ConfigurableApplicationContext context = run(database(), "dev")) {
			assertThat(admins(context)).singleElement().satisfies((admin) -> {
				assertThat(admin.get("username")).isEqualTo("admin");
				assertThat(admin.get("email")).isEqualTo("admin@localhost");
				assertThat(admin.get("password_change_required")).isEqualTo(true);
				assertThat(new BCryptPasswordEncoder().matches("password", (String) admin.get("password_hash")))
					.isTrue();
			});
			List<JsonNode> warnings = capture
				.application((line) -> "WARN".equals(field(line, "log.level"))
						&& String.valueOf(field(line, "message")).contains("Bootstrap Admin"));
			assertThat(warnings).singleElement()
				.satisfies((warning) -> assertThat(field(warning, "message")).contains("dev fallback"));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "username", "password", "email" })
	void outsideDevEachMissingValueFailsStartup(String missing) {
		List<String> args = new ArrayList<>(Arrays.asList(PROD));
		for (String arg : List.of(USERNAME, PASSWORD, EMAIL)) {
			if (!arg.startsWith("--app.bootstrap-admin." + missing + "=")) {
				args.add(arg);
			}
		}

		assertThatThrownBy(() -> run(database(), "prod", args.toArray(String[]::new)).close())
			.hasStackTraceContaining("app.bootstrap-admin." + missing + " must be injected from the secrets manager");
	}

	@Test
	void outsideDevStartupSucceedsWithEveryValueConfigured() {
		List<String> args = new ArrayList<>(Arrays.asList(PROD));
		args.addAll(List.of(USERNAME, PASSWORD, EMAIL));

		try (ConfigurableApplicationContext context = run(database(), "prod", args.toArray(String[]::new))) {
			assertThat(admins(context)).hasSize(1);
		}
	}

	@Test
	void outsideDevAConfiguredPasswordMustPassTheCredentialPolicy() {
		assertThatThrownBy(() -> run(database(), "test", USERNAME, "--app.bootstrap-admin.password=weak", EMAIL).close())
			.hasStackTraceContaining("app.bootstrap-admin.password breaks the Credential policy");
	}

	@Test
	void startupFailsWhenTheConfiguredUsernameAlreadyBelongsToAnAccount() {
		String database = database();
		try (ConfigurableApplicationContext context = run(database, "test", USERNAME, PASSWORD, EMAIL)) {
			// No Admin is left, but the configured username is now an ordinary Account's.
			context.getBean(JdbcTemplate.class).update("UPDATE users SET role = 'USER'");
		}

		assertThatThrownBy(() -> run(database, "test", USERNAME, PASSWORD,
				"--app.bootstrap-admin.email=another@test.example.com")
			.close())
			.hasStackTraceContaining("app.bootstrap-admin.username already belongs to an Account");
	}

	/**
	 * A tombstoned username can never be used again, not even by the Bootstrap Admin (ADR 0001), so an
	 * operator who deleted the admin Account must configure a different username rather than have the
	 * app silently revive the deleted one.
	 */
	@Test
	void startupFailsWhenTheConfiguredUsernameBelongsToADeletedAccount() {
		String database = database();
		try (ConfigurableApplicationContext context = run(database, "test", USERNAME, PASSWORD, EMAIL)) {
			deleteWithTombstone(context.getBean(JdbcTemplate.class));
		}

		assertThatThrownBy(() -> run(database, "test", USERNAME, PASSWORD, EMAIL).close())
			.hasStackTraceContaining("app.bootstrap-admin.username belongs to a Deleted Account");
	}

	/** What {@code DELETE /api/admin/users/{id}} leaves behind: no Account, no history, one tombstone. */
	private static void deleteWithTombstone(JdbcTemplate jdbc) {
		Map<String, Object> account = jdbc.queryForList("SELECT id, username, email FROM users").get(0);
		jdbc.update("INSERT INTO deleted_users (id, username, email, deleted_at, deleted_by) VALUES (?, ?, ?, ?, ?)",
				account.get("id"), account.get("username"), account.get("email"), Timestamp.from(Instant.now()),
				// Some other Admin did the deleting; never the deleted Account itself, which cannot.
				UUID.randomUUID());
		jdbc.update("DELETE FROM password_history");
		jdbc.update("DELETE FROM users");
	}

	@Test
	void startupFailsWhenTheConfiguredEmailAlreadyBelongsToAnAccount() {
		String database = database();
		try (ConfigurableApplicationContext context = run(database, "test", USERNAME, PASSWORD, EMAIL)) {
			context.getBean(JdbcTemplate.class).update("UPDATE users SET role = 'USER'");
		}

		assertThatThrownBy(
				() -> run(database, "test", "--app.bootstrap-admin.username=otheradmin", PASSWORD, EMAIL).close())
			.hasStackTraceContaining("app.bootstrap-admin.email already belongs to an Account");
	}

}
