package sg.example.helloauth.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import sg.example.helloauth.HelloAuthApplication;
import sg.example.helloauth.support.HttpBrowser;
import sg.example.helloauth.support.OfflineCompromisedPasswords;
import sg.example.helloauth.support.TestAccount;

/** Starts the real application twice on one database, because the rule is about a restart. */
class SessionRestartTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    private static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(HelloAuthApplication.class, OfflineCompromisedPasswords.class).run(
                "--server.port=0",
                // A named in-memory database that outlives each application context.
                "--spring.datasource.url=jdbc:h2:mem:session-restart;DB_CLOSE_DELAY=-1");
    }

    @Test
    void sessionSurvivesAnApiRestart() throws Exception {
        String sessionCookie;
        try (ConfigurableApplicationContext api = start()) {
            HttpBrowser browser = new HttpBrowser(api);
            browser.fetchCsrf();
            browser.register(ALICE);
            HttpResponse<String> login = browser.login(ALICE.username(), ALICE.password());
            assertThat(login.statusCode()).isEqualTo(200);
            sessionCookie = browser.sessionCookie();
        }

        try (ConfigurableApplicationContext api = start()) {
            HttpBrowser browser = new HttpBrowser(api);
            browser.useSessionCookie(sessionCookie);

            HttpResponse<String> hello = browser.get("/hello");

            assertThat(hello.statusCode()).isEqualTo(200);
            assertThat(hello.body()).isEqualTo("Hello, testuser1");
        }
    }
}
