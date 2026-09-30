package sg.securedhello.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import sg.securedhello.admin.AuthenticableAdmins;
import sg.securedhello.mfa.TotpWindow;
import sg.securedhello.password.PasswordService;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;
import sg.securedhello.testsupport.TestSecrets;

/**
 * The dev-only demo accounts across real startups ({@code restart}, own database): under {@code dev} they are seeded
 * once, the administrator enrolled and authenticable, and the sign-in panel's endpoint lists them with a correct current
 * code; outside {@code dev} nothing is seeded, the endpoint is 404, and a database carrying a demo account is refused.
 */
class DemoAccountsTest {

    private static final String ENDPOINT = "/api/dev/demo-accounts";
    private static final String DEMO_ON = "--app.dev.demo-accounts.enabled=true";
    private static final String USER_PASSWORD = "violet-harbour-signal-meadow";
    private static final String ADMIN_PASSWORD = "granite-falcon-ember-quarry";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path database;

    private Boot dev(Consumer<ConfigurableApplicationContext> whileRunning) {
        return RestartHarness.run(builder -> builder.profiles("dev").initializers(RestartHarness.onDatabase(database)),
                whileRunning, DEMO_ON);
    }

    private static JdbcTemplate jdbc(ConfigurableApplicationContext context) {
        return context.getBean(JdbcTemplate.class);
    }

    private static List<Map<String, Object>> users(ConfigurableApplicationContext context) {
        return jdbc(context).queryForList("SELECT * FROM users ORDER BY username");
    }

    private static HttpResponse<String> get(ConfigurableApplicationContext context, String path) {
        String port = context.getEnvironment().getProperty("local.server.port");
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Accept", "application/json").build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode panel(ConfigurableApplicationContext context) {
        HttpResponse<String> response = get(context, ENDPOINT);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValueSatisfying(
                value -> assertThat(value).contains("no-store"));
        return JSON.readTree(response.body()).get("accounts");
    }

    @Test
    void underDevBothDemoAccountsAreSeededReadyToSignInAndTheBootstrapSeedsNone() {
        Boot boot = dev(context -> {
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
            List<Map<String, Object>> users = users(context);
            assertThat(users).extracting(user -> user.get("USERNAME")).containsExactly("demo-admin", "demo-user");
            assertThat(users).allSatisfy(user -> {
                assertThat(user).containsEntry("ENABLED", true).containsEntry("FORCE_PASSWORD_CHANGE", false);
                assertThat(user.get("ACTIVATED_AT")).isNotNull();
                assertThat(user.get("CREDENTIAL_ISSUED_AT")).isNull();
            });
            assertThat(users.get(0)).containsEntry("ROLE", "ADMIN").containsEntry("EMAIL", "demo-admin@demo.invalid");
            assertThat(users.get(1)).containsEntry("ROLE", "USER").containsEntry("EMAIL", "demo-user@demo.invalid");
            assertThat(encoder.matches(ADMIN_PASSWORD, (String) users.get(0).get("PASSWORD_HASH"))).isTrue();
            assertThat(encoder.matches(USER_PASSWORD, (String) users.get(1).get("PASSWORD_HASH"))).isTrue();

            // The factor row is a sealed 69-byte envelope, as a real enrolment stores it, and the admin is authenticable.
            byte[] envelope = jdbc(context).queryForObject("SELECT totp_key FROM totp_user_details WHERE user_id = ?",
                    byte[].class, users.get(0).get("ID"));
            byte[] secret = context.getBean(DemoAccountsProperties.class).adminTotpSecretBytes();
            assertThat(envelope).hasSize(69);
            assertThat(new String(envelope, StandardCharsets.ISO_8859_1))
                    .doesNotContain(new String(secret, StandardCharsets.ISO_8859_1));
            assertThat(context.getBean(AuthenticableAdmins.class).count()).isEqualTo(1);
            assertThat(users).extracting(user -> user.get("USERNAME")).doesNotContain(TestSecrets.ADMIN_USERNAME);
        });

        assertThat(boot.failure()).isNull();
    }

    @Test
    void thePanelListsBothAccountsWithTheAdministratorsCurrentCode() {
        Boot boot = dev(context -> {
            Clock clock = context.getBean(Clock.class);
            byte[] secret = context.getBean(DemoAccountsProperties.class).adminTotpSecretBytes();
            long before = TotpWindow.counter(clock.instant());
            JsonNode accounts = panel(context);
            long after = TotpWindow.counter(clock.instant());

            assertThat(accounts).hasSize(2);
            JsonNode user = accounts.get(0);
            assertThat(user.get("username").asText()).isEqualTo("demo-user");
            assertThat(user.get("role").asText()).isEqualTo("USER");
            assertThat(user.get("seeded").asBoolean()).isTrue();
            assertThat(user.get("email").asText()).isEqualTo("demo-user@demo.invalid");
            assertThat(user.get("password").asText()).isEqualTo(USER_PASSWORD);
            assertThat(user.get("passwordChanged").asBoolean()).isFalse();
            assertThat(user.get("totp").isNull()).isTrue();

            JsonNode admin = accounts.get(1);
            assertThat(admin.get("username").asText()).isEqualTo("demo-admin");
            assertThat(admin.get("role").asText()).isEqualTo("ADMIN");
            assertThat(admin.get("seeded").asBoolean()).isTrue();
            assertThat(admin.get("email").asText()).isEqualTo("demo-admin@demo.invalid");
            assertThat(admin.get("password").asText()).isEqualTo(ADMIN_PASSWORD);
            String code = admin.get("totp").get("code").asText();
            assertThat(code).matches("\\d{6}");
            // The code TotpWindow generates for the step the request fell in, which the sign-in check accepts.
            assertThat(List.of(TotpWindow.generate(secret, before, TotpWindow.DIGITS),
                    TotpWindow.generate(secret, after, TotpWindow.DIGITS))).contains(code);
            assertThat(TotpWindow.match(secret, code, clock.instant(), TotpWindow.NEVER_USED)).isPresent();
            assertThat(admin.get("totp").get("secondsRemaining").asLong()).isBetween(1L, TotpWindow.STEP_SECONDS);

            // A changed password is flagged and no longer shown.
            UUID userId = jdbc(context).queryForObject("SELECT id FROM users WHERE username = 'demo-user'", UUID.class);
            context.getBean(PasswordService.class).setPassword(userId, "a-brand-new-quartz-harbour-lamp");
            JsonNode changed = panel(context).get(0);
            assertThat(changed.get("passwordChanged").asBoolean()).isTrue();
            assertThat(changed.get("password").isNull()).isTrue();
            assertThat(changed.get("email").asText()).isEqualTo("demo-user@demo.invalid");
        });

        assertThat(boot.failure()).isNull();
    }

    /**
     * A dev database from before the demo accounts holds {@code demo-admin} as the bootstrap seeded it (another address,
     * a forced-change credential). The seeder leaves it alone, and the panel still lists the administrator, as not
     * seeded and with nothing read from that account, rather than dropping it.
     */
    @Test
    void thePanelListsBothAccountsEvenWhenAPreDemoBootstrapAdministratorHoldsTheUsername() {
        assertThat(RestartHarness.run(
                builder -> builder.profiles("dev").initializers(RestartHarness.onDatabase(database)), context -> { },
                "--app.admin.username=demo-admin").failure()).isNull();

        Boot boot = dev(context -> {
            assertThat(jdbc(context).queryForObject("SELECT email FROM users WHERE username = 'demo-admin'",
                    String.class)).isEqualTo("demo-admin@admin.invalid");
            JsonNode accounts = panel(context);

            assertThat(accounts).extracting(account -> account.get("username").asText())
                    .containsExactly("demo-user", "demo-admin");
            assertThat(accounts.get(0).get("seeded").asBoolean()).isTrue();
            JsonNode admin = accounts.get(1);
            assertThat(admin.get("seeded").asBoolean()).isFalse();
            assertThat(admin.get("role").asText()).isEqualTo("ADMIN");
            assertThat(admin.get("email").isNull()).isTrue();
            assertThat(admin.get("password").isNull()).isTrue();
            assertThat(admin.get("totp").isNull()).isTrue();
        });

        assertThat(boot.failure()).isNull();
    }

    @Test
    void aRestartSeedsNothingAgainAndLeavesAChangedPasswordAlone() {
        assertThat(dev(context -> {
            UUID userId = jdbc(context).queryForObject("SELECT id FROM users WHERE username = 'demo-user'", UUID.class);
            context.getBean(PasswordService.class).setPassword(userId, "a-brand-new-quartz-harbour-lamp");
        }).failure()).isNull();

        Boot second = dev(context -> {
            assertThat(users(context)).hasSize(2);
            assertThat(jdbc(context).queryForObject("SELECT COUNT(*) FROM totp_user_details", Integer.class))
                    .isEqualTo(1);
            String hash = jdbc(context).queryForObject(
                    "SELECT password_hash FROM users WHERE username = 'demo-user'", String.class);
            assertThat(context.getBean(PasswordEncoder.class).matches(USER_PASSWORD, hash)).isFalse();
        });

        assertThat(second.failure()).isNull();
    }

    /**
     * Outside dev the route is unknown: no handler and no matrix row, so an anonymous call gets exactly what a route
     * that never existed gets (every missing route is refused, never 404, by the error contract; ADR-043).
     */
    @Test
    void outsideDevNothingIsSeededAndTheRouteAnswersAsAnUnknownRouteDoes() {
        Boot boot = RestartHarness.run(builder -> { }, context -> {
            assertThat(users(context)).extracting(user -> user.get("USERNAME"))
                    .containsExactly(TestSecrets.ADMIN_USERNAME);
            assertThat(context.getBeanNamesForType(DemoAccountsController.class)).isEmpty();
            assertThat(context.getBeanNamesForType(DemoAccountSeeder.class)).isEmpty();
            assertThat(context.getBeanNamesForType(DemoAccountsProperties.class)).isEmpty();
            HttpResponse<String> demo = get(context, ENDPOINT);
            HttpResponse<String> unknown = get(context, "/api/dev/no-such-route");
            assertThat(demo.statusCode()).isEqualTo(unknown.statusCode()).isEqualTo(401);
            assertThat(demo.body()).contains("AUTHENTICATION_FAILED").doesNotContain("demo-user", "demo-admin");
        });

        assertThat(boot.failure()).isNull();
    }

    @Test
    void outsideDevTheDevWhitelistIsRefused() {
        Boot boot = RestartHarness.boot(builder -> { },
                "--app.security.authorization.dev-whitelist[0].method=GET",
                "--app.security.authorization.dev-whitelist[0].path=" + ENDPOINT);

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains("app.security.authorization.dev-whitelist");
    }

    @Test
    void outsideDevADatabaseHoldingADemoAccountIsRefusedBeforeThePortOpens() {
        assertThat(dev(context -> { }).failure()).isNull();

        Boot boot = RestartHarness.boot(builder -> builder.initializers(RestartHarness.onDatabase(database)));

        assertThat(boot.portOpened()).isFalse();
        assertThat(boot.failureMessages()).contains("dev-only demo account");
    }

    @Test
    void theRecoveryRunnersNonWebContextNeverSeeds() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=dev", "app.dev.demo-accounts.enabled=true")
                .withUserConfiguration(DemoAccountSeeder.class)
                .run(context -> assertThat(context).doesNotHaveBean(DemoAccountSeeder.class));
    }
}
