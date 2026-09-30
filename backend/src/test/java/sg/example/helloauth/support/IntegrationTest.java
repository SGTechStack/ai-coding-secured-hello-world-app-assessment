package sg.example.helloauth.support;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskDecorator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import sg.example.helloauth.account.Account;

/**
 * Base for tests through the full-context HTTP seam: the real security filter chain, H2 and
 * Spring Session JDBC, driven with {@link MockMvcTester}. Every test starts with an empty
 * database, no emails sent, no background work waiting and the clock reset to {@link #START}.
 * Compromised passwords are checked offline, emails are recorded instead of sent, and background
 * work waits for the test to run it.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=" + IntegrationTest.FRONTEND_ORIGIN)
@AutoConfigureMockMvc
@Import({IntegrationTest.TestDoublesConfiguration.class, OfflineCompromisedPasswords.class})
public abstract class IntegrationTest {

    protected static final Instant START = Instant.parse("2026-01-05T09:00:00Z");

    protected static final String FRONTEND_ORIGIN = "https://app.test.example.com";

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected RecordingEmailService emails;

    @Autowired
    protected BackgroundTasks backgroundTasks;

    @Autowired
    protected JdbcClient jdbc;

    @Value("${app.api.base-path}")
    protected String basePath;

    @BeforeEach
    void resetState() {
        clock.setInstant(START);
        emails.clear();
        backgroundTasks.clear();
        jdbc.sql("DELETE FROM SPRING_SESSION_ATTRIBUTES").update();
        jdbc.sql("DELETE FROM SPRING_SESSION").update();
        jdbc.sql("DELETE FROM password_reset_tokens").update();
        jdbc.sql("DELETE FROM users").update();
    }

    protected Browser newBrowser() {
        return new Browser(mvc, basePath, null);
    }

    /** A browser whose requests come from this client address. */
    protected Browser newBrowserAt(String remoteAddress) {
        return new Browser(mvc, basePath, remoteAddress);
    }

    /** Registers this Account, makes it an Admin, and returns a browser logged in as it. */
    protected Browser loggedInAdmin(TestAccount admin) {
        Browser browser = newBrowser().registered(admin);
        jdbc.sql("UPDATE users SET role = 'ADMIN' WHERE username_key = ?").param(Account.usernameKey(admin.username()))
                .update();
        browser.login(admin).assertThat().hasStatusOk();
        browser.fetchCsrf();
        return browser;
    }

    /** The id of the Account with this username. */
    protected String idOf(TestAccount account) {
        return jdbc.sql("SELECT id FROM users WHERE username_key = ?").param(Account.usernameKey(account.username()))
                .query(String.class).single();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDoublesConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }

        @Bean
        @Primary
        RecordingEmailService recordingEmailService() {
            return new RecordingEmailService();
        }

        /** Takes the place of Boot's own executor, which backs off when this one exists. */
        @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
        BackgroundTasks backgroundTasks(TaskDecorator decorator) {
            return new BackgroundTasks(decorator);
        }
    }
}
