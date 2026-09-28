package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.helloauth.domain.Account;
import com.example.helloauth.domain.PasswordResetToken;
import com.example.helloauth.support.ApiClient;
import com.example.helloauth.support.ApiIntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Stories 6 and 7 — requesting a reset, and redeeming the token. */
class PasswordResetApiTest extends ApiIntegrationTest {

    @Test
    @DisplayName("a registered email gets a hashed, expiring token and a stubbed email")
    void issuesATokenForARegisteredEmail() {
        givenUser("alice");

        ApiClient.Response response = requestReset("alice@example.com");

        assertThat(response.status()).isEqualTo(200);
        assertThat(emails.sent()).hasSize(1);

        PasswordResetToken stored = resetTokens.findAll().getFirst();
        assertThat(stored.getUsedAt()).isNull();
        assertThat(stored.getExpiresAt()).isAfter(clock.instant());
        assertThat(stored.getExpiresAt()).isBefore(clock.instant().plus(Duration.ofMinutes(31)));

        // The database holds a hash, not the token. A leak of this table must not hand over working
        // reset links, which is the entire reason the column is called token_hash.
        String emailedToken = emails.lastToken();
        assertThat(emailedToken).isNotBlank();
        assertThat(stored.getTokenHash()).isNotEqualTo(emailedToken);
        assertThat(stored.getTokenHash()).doesNotContain(emailedToken);
        assertThat(stored.getTokenHash()).hasSize(64);
    }

    /**
     * Enumeration resistance. As with login, asserting the status code alone would be worthless: the
     * two responses have to be interchangeable, or the endpoint becomes a way to test whether an
     * address has an account.
     */
    @Test
    @DisplayName("an unregistered email gets the identical response, and no token is created")
    void unregisteredEmailIsIndistinguishable() {
        givenUser("alice");

        ApiClient.Response registered = requestReset("alice@example.com");
        ApiClient.Response unregistered = requestReset("nobody@example.com");

        assertThat(unregistered.status()).isEqualTo(registered.status());
        assertThat(unregistered.body()).isEqualTo(registered.body());
        assertThat(resetTokens.count()).as("only the real address produced a token").isEqualTo(1);
    }

    @Test
    @DisplayName("a valid token sets the new password and the old one stops working")
    void redeemingATokenChangesThePassword() {
        Account alice = givenUser("alice");
        requestReset("alice@example.com");

        ApiClient.Response response = confirmReset(emails.lastToken(), NEW_PASSWORD);

        assertThat(response.status()).isEqualTo(200);
        assertThat(login(newClient(), "alice", NEW_PASSWORD).status()).isEqualTo(200);
        assertThat(login(newClient(), "alice", PASSWORD).status()).isEqualTo(401);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, reload(alice).getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("a token works once and is refused the second time")
    void tokenIsSingleUse() {
        givenUser("alice");
        requestReset("alice@example.com");
        String token = emails.lastToken();

        assertThat(confirmReset(token, NEW_PASSWORD).status()).isEqualTo(200);

        ApiClient.Response replay = confirmReset(token, "yet-another-password");

        assertThat(replay.status()).isEqualTo(400);
        assertThat(replay.json().get("code").asText()).isEqualTo("invalid_reset_token");
        // The second attempt changed nothing, so the password from the first redemption still works.
        assertThat(login(newClient(), "alice", NEW_PASSWORD).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("an expired token is refused and the password is untouched")
    void expiredTokenIsRefused() {
        Account alice = givenUser("alice");
        requestReset("alice@example.com");
        String token = emails.lastToken();

        // Backdate the stored expiry rather than waiting: expiry is a comparison against stored
        // state, and moving the state is equivalent to moving the clock but finishes instantly.
        PasswordResetToken stored = resetTokens.findAll().getFirst();
        stored.setExpiresAt(clock.instant().minus(Duration.ofMinutes(1)));
        resetTokens.save(stored);

        ApiClient.Response response = confirmReset(token, NEW_PASSWORD);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().get("code").asText()).isEqualTo("invalid_reset_token");
        assertThat(passwordEncoder.matches(PASSWORD, reload(alice).getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("an unknown token is refused exactly like an expired one")
    void unknownTokenIsIndistinguishableFromAnExpiredOne() {
        givenUser("alice");

        ApiClient.Response response = confirmReset("a-token-that-was-never-issued", NEW_PASSWORD);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().get("code").asText()).isEqualTo("invalid_reset_token");
    }

    /**
     * A reset is what someone does when they think their password is compromised. If it left existing
     * sessions running, an attacker already holding one would keep it — and the reset would give the
     * victim a false sense that they had just locked the attacker out.
     */
    @Test
    @DisplayName("redeeming a token ends every session the account already had")
    void redemptionInvalidatesExistingSessions() {
        givenUser("alice");
        ApiClient firstDevice = loggedInAs("alice");
        ApiClient secondDevice = loggedInAs("alice");
        assertThat(firstDevice.get("/api/hello").status()).isEqualTo(200);
        assertThat(secondDevice.get("/api/hello").status()).isEqualTo(200);

        requestReset("alice@example.com");
        confirmReset(emails.lastToken(), NEW_PASSWORD);

        assertThat(firstDevice.get("/api/hello").status()).isEqualTo(401);
        assertThat(secondDevice.get("/api/hello").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("a rejected new password does not burn the token")
    void weakPasswordLeavesTheTokenUsable() {
        givenUser("alice");
        requestReset("alice@example.com");
        String token = emails.lastToken();

        ApiClient.Response rejected = confirmReset(token, "short");

        assertThat(rejected.status()).isEqualTo(400);
        assertThat(rejected.json().get("code").asText()).isEqualTo("weak_password");
        assertThat(resetTokens.findAll().getFirst().getUsedAt())
                .as("the token was not consumed")
                .isNull();
        assertThat(confirmReset(token, NEW_PASSWORD).status()).isEqualTo(200);
    }

    /**
     * Beyond the PRD's single-use rule. Without this, someone who requested a reset on a victim's
     * account before the victim did would still be holding a working key after the victim's reset.
     */
    @Test
    @DisplayName("redeeming one token retires any other outstanding token for the account")
    void redemptionRetiresSiblingTokens() {
        givenUser("alice");
        requestReset("alice@example.com");
        String firstToken = emails.lastToken();
        requestReset("alice@example.com");
        String secondToken = emails.lastToken();
        assertThat(firstToken).isNotEqualTo(secondToken);

        assertThat(confirmReset(secondToken, NEW_PASSWORD).status()).isEqualTo(200);
        assertThat(confirmReset(firstToken, "a-third-password-here").status()).isEqualTo(400);
    }

    private ApiClient.Response requestReset(String email) {
        return client.post("/api/auth/password-reset/request", Map.of("email", email));
    }

    private ApiClient.Response confirmReset(String token, String password) {
        return client.post(
                "/api/auth/password-reset/confirm", Map.of("token", token, "password", password));
    }

    private Account reload(Account account) {
        return accounts.findById(account.getId()).orElseThrow();
    }
}
