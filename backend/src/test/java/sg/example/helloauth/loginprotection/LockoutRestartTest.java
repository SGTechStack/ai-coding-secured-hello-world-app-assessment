package sg.example.helloauth.loginprotection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import sg.example.helloauth.HelloAuthApplication;
import sg.example.helloauth.support.HttpBrowser;
import sg.example.helloauth.support.OfflineCompromisedPasswords;
import sg.example.helloauth.support.TestAccount;

/** Starts the real application twice on one database, because the rule is about a restart. */
class LockoutRestartTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    private static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(HelloAuthApplication.class, OfflineCompromisedPasswords.class).run(
                "--server.port=0",
                // A named in-memory database that outlives each application context.
                "--spring.datasource.url=jdbc:h2:mem:lockout-restart;DB_CLOSE_DELAY=-1");
    }

    @Test
    void lockSurvivesAnApiRestart() throws Exception {
        try (ConfigurableApplicationContext api = start()) {
            HttpBrowser browser = new HttpBrowser(api);
            browser.fetchCsrf();
            browser.register(ALICE);
            for (int i = 0; i < 5; i++) {
                assertThat(browser.login(ALICE.username(), "wrong-password-" + i).statusCode()).isEqualTo(401);
            }
        }

        try (ConfigurableApplicationContext api = start()) {
            HttpBrowser browser = new HttpBrowser(api);
            browser.fetchCsrf();

            assertThat(browser.login(ALICE.username(), ALICE.password()).statusCode()).isEqualTo(401);
        }
    }
}
