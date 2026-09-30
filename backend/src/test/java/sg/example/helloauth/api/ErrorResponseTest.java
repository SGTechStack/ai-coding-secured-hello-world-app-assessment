package sg.example.helloauth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.TestAccount;

class ErrorResponseTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    @Test
    void unknownPathForARegularUserIsNotFound() {
        Browser browser = newBrowser().registerAndLogin(ALICE);

        assertProblem(browser.get("/no-such-thing"), 404, "not found");
    }

    @Test
    void wrongMethodGetsAProblemWithAStableCode() {
        Browser browser = newBrowser().registerAndLogin(ALICE);

        MvcTestResult result = browser.send(browser.withCsrf(mvc.delete().uri(basePath + "/hello")));

        assertProblem(result, 405, "method not allowed");
    }

    /** What the servlet container does when it rejects a request before Spring MVC sees it. */
    @Test
    void containerErrorDispatchIsAProblemWithoutInternals() {
        MvcTestResult result = mvc.get().uri("/error")
                .with(request -> {
                    request.setDispatcherType(DispatcherType.ERROR);
                    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
                    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("secret detail"));
                    return request;
                })
                .exchange();

        assertProblem(result, 400, "validation failed");
        assertThat(result.getResponse().getContentAsByteArray()).asString()
                .doesNotContain("secret detail", "IllegalStateException", "trace");
    }
}
