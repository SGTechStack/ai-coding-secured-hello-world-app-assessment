package com.eitri.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eitri.EitriApplication;
import com.eitri.testsupport.TestAccounts;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Starts the application itself, with a unique in-memory database per case. */
@ExtendWith(OutputCaptureExtension.class)
class AdminBootstrapIT {

    private static final String BOOTSTRAP_PASSWORD = "bootstrap-admin-secret-1";

    @Test
    @DisplayName("[assessment/story12-ac1] the first start seeds an admin from configuration")
    void firstStartSeedsTheAdmin(CapturedOutput output) throws Exception {
        String database = uniqueDatabase();
        try (ConfigurableApplicationContext context = start(database, "admin", "admin@example.com", BOOTSTRAP_PASSWORD)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

            List<Map<String, Object>> admins = jdbc.queryForList("SELECT * FROM users WHERE role = 'ADMIN'");
            assertThat(admins).singleElement().satisfies(admin -> {
                assertThat(admin).containsEntry("USERNAME", "admin")
                        .containsEntry("EMAIL", "admin@example.com")
                        .containsEntry("ENABLED", true);
                assertThat((String) admin.get("PASSWORD_HASH")).startsWith("{bcrypt}$2a$10$");
                assertThat(TestAccounts.bcryptMatches(BOOTSTRAP_PASSWORD, (String) admin.get("PASSWORD_HASH")))
                        .isTrue();
            });
            assertThat(loginStatus(context, "admin", BOOTSTRAP_PASSWORD)).isEqualTo(200);
            assertThat(output.getAll()).contains("Initial admin account created");
        }
        assertThat(output.getAll()).doesNotContain(BOOTSTRAP_PASSWORD);
    }

    @Test
    void theSeededAdminIsStoredLowerCase() {
        try (ConfigurableApplicationContext context =
                start(uniqueDatabase(), "RootAdmin", "Root@Example.com", BOOTSTRAP_PASSWORD)) {
            assertThat(context.getBean(JdbcTemplate.class)
                            .queryForMap("SELECT username, email FROM users WHERE role = 'ADMIN'"))
                    .containsEntry("USERNAME", "rootadmin")
                    .containsEntry("EMAIL", "root@example.com");
        }
    }

    @Test
    @DisplayName("[assessment/story12-ac2] a restart creates no duplicate and keeps the existing admin's password")
    void restartKeepsTheExistingAdmin() throws Exception {
        String database = uniqueDatabase();
        try (ConfigurableApplicationContext first = start(database, "admin", "admin@example.com", BOOTSTRAP_PASSWORD)) {
            first.getBean(JdbcTemplate.class).update(
                    "UPDATE users SET password_hash = ? WHERE username = 'admin'",
                    TestAccounts.bcrypt("changed-by-admin-later"));
        }

        try (ConfigurableApplicationContext second = start(database, "admin", "admin@example.com", BOOTSTRAP_PASSWORD)) {
            JdbcTemplate jdbc = second.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Integer.class))
                    .isOne();
            String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'admin'", String.class);
            assertThat(TestAccounts.bcryptMatches("changed-by-admin-later", hash)).isTrue();
        }
    }

    @Test
    void anExistingAdminNeedsNoBootstrapConfiguration() {
        String database = uniqueDatabase();
        start(database, "admin", "admin@example.com", BOOTSTRAP_PASSWORD).close();

        try (ConfigurableApplicationContext restarted = start(database, "", "", "")) {
            assertThat(restarted.getBean(JdbcTemplate.class)
                            .queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Integer.class))
                    .isOne();
        }
    }

    @Test
    @DisplayName("[assessment/story12-ac3] startup fails naming the missing property when no admin exists")
    void startupFailsWithoutAdminCredentials() throws Exception {
        String database = uniqueDatabase();

        assertThatThrownBy(() -> start(database, "admin", "admin@example.com", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.admin.password must be configured");
        assertThat(adminCount(database)).isZero();
        assertThat(userCount(database)).isEqualTo(1); // only the seeded johndoe

        assertThatThrownBy(() -> start(uniqueDatabase(), " ", "admin@example.com", BOOTSTRAP_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.admin.username must be configured");
        assertThatThrownBy(() -> start(uniqueDatabase(), "admin", "", BOOTSTRAP_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.admin.email must be configured");
    }

    @Test
    @DisplayName("[assessment/story12-ac4] startup fails when the admin password fails the policy, without echoing it")
    void startupFailsWithAWeakAdminPassword(CapturedOutput output) throws Exception {
        String database = uniqueDatabase();

        assertThatThrownBy(() -> start(database, "admin", "admin@example.com", "short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin password does not meet the password policy")
                .message()
                .doesNotContain("short");
        assertThat(adminCount(database)).isZero();

        String canary = "weak-canary";
        assertThatThrownBy(() -> start(uniqueDatabase(), "admin", "admin@example.com", canary))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(canary);
        assertThat(output.getAll()).doesNotContain(canary);
    }

    @Test
    void startupFailsWhenTheAdminUsernameOrEmailBelongsToANonAdmin() throws Exception {
        String database = uniqueDatabase();

        assertThatThrownBy(() -> start(database, "JohnDoe", "admin@example.com", BOOTSTRAP_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.admin.username is already used by a non-admin account");
        assertThatThrownBy(() -> start(uniqueDatabase(), "admin", "john@example.com", BOOTSTRAP_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.admin.email is already used by a non-admin account");
        assertThat(adminCount(database)).isZero();
    }

    private static ConfigurableApplicationContext start(
            String database, String username, String email, String password) {
        // Command-line arguments outrank the profile files (builder properties() would not).
        List<String> arguments = new ArrayList<>(List.of(
                "--spring.datasource.url=" + url(database),
                "--app.admin.username=" + username,
                "--app.admin.email=" + email,
                "--app.admin.password=" + password,
                "--spring.main.banner-mode=off",
                "--server.port=0"));
        // Servlet web application: Spring Session (used by the account module) needs it.
        return new SpringApplicationBuilder(EitriApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("test")
                .run(arguments.toArray(String[]::new));
    }

    /** Logs in over HTTP against the started server, through the public CSRF and login endpoints. */
    private static int loginStatus(ConfigurableApplicationContext context, String username, String password)
            throws Exception {
        String base = "http://localhost:" + context.getEnvironment().getProperty("local.server.port") + "/api/v1";
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> csrf = client.send(
                HttpRequest.newBuilder(URI.create(base + "/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        String token = JsonPath.read(csrf.body(), "$.token");
        // The SESSION cookie is Secure outside dev, so an HTTP cookie jar would drop it; send it by hand.
        String sessionCookie = csrf.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        HttpResponse<String> login = client.send(
                HttpRequest.newBuilder(URI.create(base + "/auth/login"))
                        .header("Cookie", sessionCookie)
                        .header("Content-Type", "application/json")
                        .header("X-CSRF-TOKEN", token)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return login.statusCode();
    }

    private static String uniqueDatabase() {
        return "admin-bootstrap-" + UUID.randomUUID();
    }

    private static String url(String database) {
        return "jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1";
    }

    private static int adminCount(String database) throws Exception {
        return count(database, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'");
    }

    private static int userCount(String database) throws Exception {
        return count(database, "SELECT COUNT(*) FROM users");
    }

    private static int count(String database, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url(database), "sa", "");
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
