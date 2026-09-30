package sg.example.helloauth.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import sg.example.helloauth.HelloAuthApplication;
import sg.example.helloauth.support.HttpBrowser;
import sg.example.helloauth.support.OfflineCompromisedPasswords;
import sg.example.helloauth.support.TestAccount;

/**
 * Starts the real application, because the rules are about startup. The Bootstrap admin's
 * settings come from the test configuration, as {@link TestAccount#BOOTSTRAP_ADMIN}.
 */
class BootstrapAdminTest {

    private static final TestAccount ADMIN = TestAccount.BOOTSTRAP_ADMIN;

    private static final AtomicInteger databases = new AtomicInteger();

    /** A fresh in-memory database that outlives each application context, for restarts. */
    private static String newDatabase() {
        return "--spring.datasource.url=jdbc:h2:mem:bootstrap-admin-" + databases.incrementAndGet()
                + ";DB_CLOSE_DELAY=-1";
    }

    private static ConfigurableApplicationContext start(String database, String... args) {
        String[] common = {"--server.port=0", database};
        return new SpringApplicationBuilder(HelloAuthApplication.class, OfflineCompromisedPasswords.class)
                .run(Stream.concat(Stream.of(common), Stream.of(args)).toArray(String[]::new));
    }

    private static int adminCount(ConfigurableApplicationContext api) {
        return api.getBean(JdbcClient.class).sql("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")
                .query(Integer.class).single();
    }

    @Test
    void bootstrapAdminIsCreatedAtFirstStartupAndCanLogInAsAnAdmin() throws Exception {
        try (ConfigurableApplicationContext api = start(newDatabase())) {
            HttpBrowser browser = new HttpBrowser(api);
            browser.fetchCsrf();

            assertThat(browser.login(ADMIN.username(), ADMIN.password()).statusCode()).isEqualTo(200);
            HttpResponse<String> me = browser.get("/me");
            assertThat(JsonPath.<String>read(me.body(), "$.role")).isEqualTo("ADMIN");
            assertThat(JsonPath.<String>read(me.body(), "$.email")).isEqualTo(ADMIN.email());
        }
    }

    @Test
    void bootstrapAdminPasswordIsHashedLikeAnyOther() {
        try (ConfigurableApplicationContext api = start(newDatabase())) {
            String hash = api.getBean(JdbcClient.class).sql("SELECT password_hash FROM users").query(String.class)
                    .single();

            assertThat(hash).startsWith("{bcrypt}$2a$12$").doesNotContain(ADMIN.password());
        }
    }

    @Test
    void restartCreatesNoDuplicate() {
        String database = newDatabase();
        try (ConfigurableApplicationContext api = start(database)) {
            assertThat(adminCount(api)).isOne();
        }

        try (ConfigurableApplicationContext api = start(database)) {
            assertThat(adminCount(api)).isOne();
        }
    }

    @Test
    void noBootstrapAdminIsCreatedWhileAnotherActiveAdminExists() {
        String database = newDatabase();
        try (ConfigurableApplicationContext ignored = start(database)) {
            // The first start creates one.
        }

        try (ConfigurableApplicationContext api = start(database, "--app.admin.username=testadmin2",
                "--app.admin.email=testadmin2@test.example.com")) {
            assertThat(adminCount(api)).isOne();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "password", "email"})
    void startupFailsOutsideTheDevProfileWhenASettingIsMissing(String setting) {
        assertThatThrownBy(() -> start(newDatabase(), "--app.admin." + setting + "=").close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.admin.username, app.admin.password and app.admin.email must all be set");
    }

    @Test
    void startupFailsWhenThePasswordBreaksThePasswordPolicy() {
        assertThatThrownBy(() -> start(newDatabase(), "--app.admin.password=password").close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.admin.password must be at least 12 characters, "
                        + "must not be a password known from data breaches");
    }

    @ParameterizedTest
    @ValueSource(strings = {"--app.admin.username=has space", "--app.admin.email=not-an-email"})
    void startupFailsWhenTheUsernameOrEmailBreaksTheRegistrationRules(String setting) {
        assertThatThrownBy(() -> start(newDatabase(), setting).close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith(setting.substring(2, setting.indexOf('=')) + " must ");
    }

    @Test
    void devProfileMayStartWithoutTheSettingsAndThenCreatesNoAdmin() {
        try (ConfigurableApplicationContext api = start(newDatabase(), "--spring.profiles.active=dev",
                "--app.admin.username=", "--app.admin.password=", "--app.admin.email=")) {
            assertThat(adminCount(api)).isZero();
        }
    }
}
