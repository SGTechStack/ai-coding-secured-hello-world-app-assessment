package com.example.hello;

import com.example.hello.reset.EmailService;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class ResetIntegrationTest extends HttpIntegrationSupport {
    @MockitoBean EmailService emails;

    @Test
    void resetRequestIsGenericAndOnlyTokenHashPersists() throws Exception {
        Browser browser = new Browser();
        String username = register(browser);
        var known = browser.mutate("POST", "/api/auth/password-reset/request", Map.of("email", username + "@example.com"));
        var unknown = browser.mutate("POST", "/api/auth/password-reset/request", Map.of("email", "missing@example.com"));
        assertThat(known.statusCode()).isEqualTo(200);
        assertThat(unknown.statusCode()).isEqualTo(200);
        assertThat(known.body()).isEqualTo(unknown.body());
        String token = deliveredToken(username);
        String hash = database.queryForObject("select token_hash from password_reset_tokens where user_id = (select id from users where username = ?)", String.class, username);
        assertThat(hash).hasSize(64).doesNotContain(token);
    }

    @Test
    void resetIsSingleUseRevokesAllSessionsAndChangesPassword() throws Exception {
        Browser first = new Browser();
        Browser second = new Browser();
        String username = register(first);
        login(first, username, "A-strong-password-2026");
        login(second, username, "A-strong-password-2026");
        String token = requestToken(username);
        assertThat(confirm(new Browser(), token, "Replacement-password-2026")).isEqualTo(200);
        assertThat(first.get("/api/hello").statusCode()).isEqualTo(401);
        assertThat(second.get("/api/hello").statusCode()).isEqualTo(401);
        assertThat(confirm(new Browser(), token, "Yet-another-password-2026")).isEqualTo(400);
        assertThat(login(new Browser(), username, "A-strong-password-2026").statusCode()).isEqualTo(401);
        assertThat(login(new Browser(), username, "Replacement-password-2026").statusCode()).isEqualTo(200);
        assertThat(database.queryForObject("select count(*) from SPRING_SESSION where PRINCIPAL_NAME = ?", Integer.class, username)).isEqualTo(1);
    }

    @Test
    void expiredInvalidAndWeakPasswordTokensDoNotChangePassword() throws Exception {
        String username = register(new Browser());
        String token = requestToken(username);
        assertThat(confirm(new Browser(), token, "short")).isEqualTo(400);
        assertThat(confirm(new Browser(), "invalid-token", "Replacement-password-2026")).isEqualTo(400);
        database.update("update password_reset_tokens set expires_at = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) where user_id = (select id from users where username = ?)", username);
        assertThat(confirm(new Browser(), token, "Replacement-password-2026")).isEqualTo(400);
        assertThat(login(new Browser(), username, "A-strong-password-2026").statusCode()).isEqualTo(200);
    }

    @Test
    void concurrentRedemptionSucceedsExactlyOnce() throws Exception {
        String token = requestToken(register(new Browser()));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Integer> attempt = () -> { Browser browser = new Browser(); start.await(); return confirm(browser, token, "Replacement-password-2026"); };
            Future<Integer> first = executor.submit(attempt);
            Future<Integer> second = executor.submit(attempt);
            start.countDown();
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 400);
        }
    }

    private int confirm(Browser browser, String token, String password) throws Exception {
        return browser.mutate("POST", "/api/auth/password-reset/confirm", Map.of("token", token, "password", password)).statusCode();
    }

    private String requestToken(String username) throws Exception {
        assertThat(new Browser().mutate("POST", "/api/auth/password-reset/request", Map.of("email", username + "@example.com")).statusCode()).isEqualTo(200);
        return deliveredToken(username);
    }

    private String deliveredToken(String username) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emails).sendPasswordResetEmail(eq(username + "@example.com"), link.capture());
        return URI.create(link.getValue()).getFragment().replace("token=", "");
    }
}
