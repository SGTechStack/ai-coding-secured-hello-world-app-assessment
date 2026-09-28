package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.helloauth.domain.Account;
import com.example.helloauth.support.ApiClient;
import com.example.helloauth.support.ApiIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Story 2 — login. The four cases the PRD names: success, wrong password, unknown username, and a
 * locked account.
 */
class LoginApiTest extends ApiIntegrationTest {

    @Test
    @DisplayName("correct credentials create a session, set an HttpOnly cookie and reset the counter")
    void successfulLogin() {
        Account alice = givenUser("alice");
        // Arrive with a couple of failures already on the record, so "resets to 0" has something to
        // reset. Starting from zero would make the assertion pass without the behaviour existing.
        failLogin("alice");
        failLogin("alice");
        assertThat(reload(alice).getFailedLoginAttempts()).isEqualTo(2);

        ApiClient session = newClient();
        ApiClient.Response response = login(session, "alice", PASSWORD);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("authenticated").asBoolean()).isTrue();
        assertThat(response.json().get("username").asText()).isEqualTo("alice");
        assertThat(response.json().get("role").asText()).isEqualTo("USER");

        assertThat(session.sessionCookie()).isNotBlank();
        String setCookie =
                response.headers().all("set-cookie").stream()
                        .filter(value -> value.startsWith("SESSION="))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No SESSION cookie was set"));
        assertThat(setCookie).containsIgnoringCase("HttpOnly");

        assertThat(reload(alice).getFailedLoginAttempts()).isZero();
        assertThat(session.get("/api/hello").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("a wrong password is refused generically and increments the failure counter")
    void wrongPassword() {
        Account alice = givenUser("alice");

        ApiClient.Response response = login(newClient(), "alice", "not-the-password");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.json().get("code").asText()).isEqualTo("invalid_credentials");
        assertThat(reload(alice).getFailedLoginAttempts()).isEqualTo(1);
    }

    /**
     * The point of this test is not the 401 — it is that the 401 is <em>byte-for-byte</em> the same as
     * the wrong-password one. Asserting only the status code would let a helpful "no such user"
     * message slip in and still pass, which is exactly the leak the PRD is guarding against.
     */
    @Test
    @DisplayName("an unknown username is indistinguishable from a wrong password")
    void unknownUsernameIsIndistinguishable() {
        givenUser("alice");

        ApiClient.Response wrongPassword = login(newClient(), "alice", "not-the-password");
        ApiClient.Response unknownUser = login(newClient(), "nobody-here", "not-the-password");

        assertThat(unknownUser.status()).isEqualTo(wrongPassword.status());
        assertThat(unknownUser.body()).isEqualTo(wrongPassword.body());
        // The message may well mention the word "username" — "Invalid username or password" is
        // generic precisely because it names both and commits to neither. What it must not do is
        // assert anything about existence.
        assertThat(unknownUser.json().get("message").asText())
                .doesNotContainIgnoringCase("no such")
                .doesNotContainIgnoringCase("not found")
                .doesNotContainIgnoringCase("does not exist")
                .doesNotContainIgnoringCase("unknown")
                .doesNotContainIgnoringCase("locked")
                .doesNotContainIgnoringCase("disabled");
    }

    @Test
    @DisplayName("a locked account is refused even when the password is right")
    void lockedAccountIsRefusedWithCorrectPassword() {
        Account alice = givenUser("alice");
        alice.lockUntil(clock.instant().plus(Duration.ofMinutes(30)));
        accounts.save(alice);

        ApiClient.Response response = login(newClient(), "alice", PASSWORD);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.json().get("code").asText()).isEqualTo("invalid_credentials");
        assertThat(client.get("/api/hello").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("a disabled account cannot log in, and is not told why")
    void disabledAccountCannotLogIn() {
        Account alice = givenUser("alice");
        alice.setEnabled(false);
        accounts.save(alice);

        ApiClient.Response disabled = login(newClient(), "alice", PASSWORD);
        ApiClient.Response wrongPassword = login(newClient(), "alice", "not-the-password");

        assertThat(disabled.status()).isEqualTo(401);
        assertThat(disabled.body()).isEqualTo(wrongPassword.body());
    }

    @Test
    @DisplayName("usernames are matched without regard to case")
    void usernameIsCaseInsensitive() {
        givenUser("alice");

        assertThat(login(newClient(), "ALICE", PASSWORD).status()).isEqualTo(200);
    }

    /**
     * Session fixation: the identifier a client held before logging in must not be the one it holds
     * afterwards, or an attacker who planted it is authenticated too.
     */
    @Test
    @DisplayName("logging in replaces any session identifier the client already had")
    void loginReplacesTheExistingSession() {
        givenUser("alice");

        ApiClient session = newClient();
        login(session, "alice", "not-the-password");
        String beforeLogin = session.sessionCookie();

        login(session, "alice", PASSWORD);
        String afterLogin = session.sessionCookie();

        assertThat(afterLogin).isNotBlank();
        if (beforeLogin != null) {
            assertThat(afterLogin).isNotEqualTo(beforeLogin);
        }
    }

    private void failLogin(String username) {
        login(newClient(), username, "not-the-password");
    }

    private Account reload(Account account) {
        return accounts.findById(account.getId()).orElseThrow();
    }
}
