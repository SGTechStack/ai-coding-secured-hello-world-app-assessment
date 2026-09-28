package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.helloauth.domain.Account;
import com.example.helloauth.support.ApiIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 3, account half — lockout after repeated failures, and its expiry.
 *
 * <p>The cooldown is shortened to two seconds for this context so the expiry path can be exercised
 * for real. The alternative would be to reach into the database and backdate {@code locked_until},
 * which tests the assertion rather than the mechanism: it proves the query works, not that a lockout
 * actually lifts on its own.
 */
@TestPropertySource(properties = {"app.lockout.max-failed-attempts=5", "app.lockout.cooldown=2s"})
class AccountLockoutApiTest extends ApiIntegrationTest {

    @Test
    @DisplayName("the fifth consecutive failure locks the account")
    void locksAfterFiveFailures() {
        Account alice = givenUser("alice");

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(login(newClient(), "alice", "wrong").status()).isEqualTo(401);
            assertThat(reload(alice).getLockedUntil())
                    .as("still unlocked after %d failures", attempt)
                    .isNull();
        }

        assertThat(login(newClient(), "alice", "wrong").status()).isEqualTo(401);

        Account locked = reload(alice);
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isNotNull();
        assertThat(locked.isLocked(clock.instant())).isTrue();
    }

    /** Decision 10: attempts against a locked account neither count nor extend the lockout. */
    @Test
    @DisplayName("hammering a locked account does not extend the lockout")
    void attemptsAgainstALockedAccountChangeNothing() {
        Account alice = givenUser("alice");
        lockOut("alice");

        Account afterLock = reload(alice);
        Instant deadline = afterLock.getLockedUntil();
        int attempts = afterLock.getFailedLoginAttempts();

        login(newClient(), "alice", "wrong");
        login(newClient(), "alice", PASSWORD);

        Account afterHammering = reload(alice);
        assertThat(afterHammering.getLockedUntil()).isEqualTo(deadline);
        assertThat(afterHammering.getFailedLoginAttempts()).isEqualTo(attempts);
    }

    @Test
    @DisplayName("once the cooldown elapses the correct password works and the counter resets")
    void lockoutLiftsItselfAndResetsTheCounter() throws InterruptedException {
        Account alice = givenUser("alice");
        lockOut("alice");

        assertThat(login(newClient(), "alice", PASSWORD).status())
                .as("still locked immediately after the lockout")
                .isEqualTo(401);

        // Just past the two-second cooldown configured for this context.
        Thread.sleep(2_200);

        assertThat(login(newClient(), "alice", PASSWORD).status()).isEqualTo(200);

        Account unlocked = reload(alice);
        assertThat(unlocked.getFailedLoginAttempts()).isZero();
        assertThat(unlocked.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("a lockout that has expired leaves the user a full allowance, not one attempt")
    void expiredLockoutClearsTheCounterEvenOnAFailedAttempt() throws InterruptedException {
        Account alice = givenUser("alice");
        lockOut("alice");

        Thread.sleep(2_200);

        // A wrong password after the lockout expires: the stale count of 5 is cleared first, so this
        // is failure number one rather than number six, and must not re-lock the account.
        assertThat(login(newClient(), "alice", "wrong").status()).isEqualTo(401);

        Account after = reload(alice);
        assertThat(after.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(after.isLocked(clock.instant())).isFalse();
    }

    private void lockOut(String username) {
        for (int attempt = 0; attempt < 5; attempt++) {
            login(newClient(), username, "wrong");
        }
    }

    private Account reload(Account account) {
        return accounts.findById(account.getId()).orElseThrow();
    }
}
