package com.example.securedhello;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The Postgres and MySQL Flyway migrations, run against a real Postgres and a real MySQL. Every
 * other test uses H2, so nothing else would notice a migration only H2 accepts.
 * <p>
 * Booting the whole application is the assertion: Flyway applies
 * {@code classpath:db/migration/{vendor}} at startup, then Hibernate's {@code ddl-auto=validate}
 * refuses to start when the schema and the entities disagree. The Bootstrap Admin row that follows
 * proves the vendor's own column types accept what the app writes — the UUID primary key in
 * particular, which Postgres stores as {@code uuid} and MySQL as {@code binary(16)}.
 * <p>
 * No profile is active, so this also exercises the production configuration rules: the CORS
 * allowlist, the IP-hash key and the Bootstrap Admin values are all required, and are supplied here
 * as synthetic values.
 * <p>
 * Opt-in: it needs a working Docker daemon and pulls two database images, which the ordinary build
 * must not depend on. Run it with {@code ./mvnw -P migrations test -Dtest=NonH2MigrationTest}
 * before any deployment targeting Postgres or MySQL, and after any change to a migration or an
 * entity. Outside that profile it is not even compiled.
 */
class NonH2MigrationTest {

	/** Every migration in one vendor's directory: V1 spring_session … V4 deleted_users. */
	private static final int MIGRATION_COUNT = 4;

	@Test
	void postgresMigrationsApplyAndTheSchemaMatchesTheEntities() {
		try (PostgreSQLContainer database = new PostgreSQLContainer("postgres:16-alpine")) {
			assertTheApplicationStartsAgainst(database, "Postgres");
		}
	}

	@Test
	void mysqlMigrationsApplyAndTheSchemaMatchesTheEntities() {
		try (MySQLContainer database = new MySQLContainer("mysql:8.0")) {
			assertTheApplicationStartsAgainst(database, "MySQL");
		}
	}

	private static void assertTheApplicationStartsAgainst(JdbcDatabaseContainer<?> database, String vendor) {
		database.start();
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BackendApplication.class)
			.run("--server.port=0", "--management.server.port=0",
					"--spring.datasource.url=" + database.getJdbcUrl(),
					"--spring.datasource.username=" + database.getUsername(),
					"--spring.datasource.password=" + database.getPassword(),
					"--app.cors.allowed-origins=https://app.example.invalid",
					"--app.ip-hash.key=synthetic-migration-test-key",
					"--app.bootstrap-admin.username=migrationadmin",
					"--app.bootstrap-admin.password=Synthetic-Migration-Pass-42",
					"--app.bootstrap-admin.email=migrationadmin@test.example.com")) {
			assertThat(context.isRunning()).as("the application started against %s", vendor).isTrue();
			JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
					Integer.class))
				.as("every migration in db/migration/{vendor} applied on %s", vendor)
				.isEqualTo(MIGRATION_COUNT);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Integer.class))
				.as("the Bootstrap Admin was written to and read back from %s", vendor)
				.isEqualTo(1);
		}
	}

}
