package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.helloauth.domain.Account;
import com.example.helloauth.support.ApiClient;
import com.example.helloauth.support.ApiIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 3, address half — per-IP throttling, and its independence from account lockout.
 *
 * <p>Independence is the whole requirement, and the PRD says why: if the only brake were per-account,
 * an attacker could lock a victim out of their own account by failing that victim's password a few
 * times. So these tests spread failures across several accounts, where no single account comes close
 * to its own lockout threshold, and check that the source address is still stopped.
 */
@TestPropertySource(properties = {"app.throttle.max-failures=3", "app.throttle.window=5m"})
class IpThrottleApiTest extends ApiIntegrationTest {

    @Test
    @DisplayName("failures spread across accounts throttle the address without locking any account")
    void throttlesTheAddressWhileAccountsStayUnlocked() {
        Account alice = givenUser("alice");
        Account bob = givenUser("bob");
        Account carol = givenUser("carol");

        assertThat(login(client, "alice", "wrong").status()).isEqualTo(401);
        assertThat(login(client, "bob", "wrong").status()).isEqualTo(401);
        assertThat(login(client, "carol", "wrong").status()).isEqualTo(401);

        ApiClient.Response fourth = login(client, "alice", "wrong");
        assertThat(fourth.status()).isEqualTo(429);
        assertThat(fourth.json().get("code").asText()).isEqualTo("too_many_requests");

        // One failure each, against a threshold of five: the accounts themselves are untouched, so
        // the brake that stopped the fourth request can only have been the address-level one.
        for (Account account : new Account[] {alice, bob, carol}) {
            Account reloaded = accounts.findById(account.getId()).orElseThrow();
            assertThat(reloaded.getFailedLoginAttempts()).isEqualTo(1);
            assertThat(reloaded.isLocked(clock.instant())).isFalse();
        }
    }

    @Test
    @DisplayName("a throttled address is refused even with correct credentials")
    void throttlingOutranksCorrectCredentials() {
        givenUser("alice");
        givenUser("bob");
        givenUser("carol");
        Account dave = givenUser("dave");

        login(client, "alice", "wrong");
        login(client, "bob", "wrong");
        login(client, "carol", "wrong");

        ApiClient.Response response = login(client, "dave", PASSWORD);

        assertThat(response.status()).isEqualTo(429);
        // Dave's account is spotless. He is refused because of where the request came from, which is
        // the trade-off throttling makes: a shared address can be punished for a neighbour's traffic.
        Account reloaded = accounts.findById(dave.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();
        assertThat(reloaded.isLocked(clock.instant())).isFalse();
    }

    @Test
    @DisplayName("the password reset request endpoint is metered too, on every call")
    void resetRequestsCountTowardsTheSameAllowance() {
        givenUser("alice");

        // Every call counts here, not only the ones that match an account — the endpoint cannot tell
        // the caller which happened, so there is no "failure" to count and counting only failures
        // would leave it entirely unmetered.
        for (int attempt = 0; attempt < 3; attempt++) {
            ApiClient.Response response = requestReset("nobody-" + attempt + "@example.com");
            assertThat(response.status()).isEqualTo(200);
        }

        assertThat(requestReset("alice@example.com").status()).isEqualTo(429);
        assertThat(emails.sent()).isEmpty();
    }

    private ApiClient.Response requestReset(String email) {
        return client.post("/api/auth/password-reset/request", Map.of("email", email));
    }
}
