package sg.example.helloauth.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.TestAccount;

@TestPropertySource(properties = "app.api.base-path=/custom/v1")
class BasePathTest extends IntegrationTest {

    @Test
    void wholeFlowWorksUnderAConfiguredBasePath() {
        Browser browser = newBrowser().registerAndLogin(TestAccount.testUser(1));

        assertThat(browser.get("/hello")).hasStatusOk().hasBodyTextEqualTo("Hello, testuser1");
        assertThat(mvc.get().uri("/api/csrf").exchange()).hasStatus(401);
    }
}
