package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.helloauth.domain.Account;
import com.example.helloauth.domain.Role;
import com.example.helloauth.support.ApiClient;
import com.example.helloauth.support.ApiIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Story 1 — a visitor registers an account. */
class RegistrationApiTest extends ApiIntegrationTest {

    @Test
    @DisplayName("a unique username, email and strong password creates an enabled USER account")
    void registersAccount() {
        ApiClient.Response response = register("alice", "alice@example.com", PASSWORD);

        assertThat(response.status()).isEqualTo(201);

        Account created = accounts.findByUsername("alice").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.USER);
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.getFailedLoginAttempts()).isZero();
        assertThat(created.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("the password is stored as a BCrypt hash and the plaintext is nowhere in the row")
    void storesBcryptHashOnly() {
        register("alice", "alice@example.com", PASSWORD);

        Account created = accounts.findByUsername("alice").orElseThrow();
        assertThat(created.getPasswordHash()).startsWith("$2");
        assertThat(created.getPasswordHash()).doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, created.getPasswordHash())).isTrue();

        // Nothing else on the account echoes the password either — a hash is not much use if the
        // plaintext also ended up in some adjacent field.
        assertThat(created.getUsername()).doesNotContain(PASSWORD);
        assertThat(created.getEmail()).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("a taken username is rejected with a named conflict and creates nothing")
    void rejectsDuplicateUsername() {
        register("alice", "alice@example.com", PASSWORD);

        ApiClient.Response response = register("alice", "someone-else@example.com", PASSWORD);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.json().get("code").asText()).isEqualTo("registration_conflict");
        assertThat(response.json().get("fieldErrors").has("username")).isTrue();
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a taken email is rejected regardless of the case it is typed in")
    void rejectsDuplicateEmailIgnoringCase() {
        register("alice", "alice@example.com", PASSWORD);

        ApiClient.Response response = register("bob", "ALICE@Example.COM", PASSWORD);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.json().get("fieldErrors").has("email")).isTrue();
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a password shorter than the policy minimum is rejected and creates nothing")
    void rejectsShortPassword() {
        ApiClient.Response response = register("alice", "alice@example.com", "short");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().get("code").asText()).isEqualTo("weak_password");
        assertThat(accounts.count()).isZero();
    }

    @Test
    @DisplayName("a password past BCrypt's 72-byte limit is refused rather than silently truncated")
    void rejectsOverlongPassword() {
        ApiClient.Response response =
                register("alice", "alice@example.com", "x".repeat(73));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().get("code").asText()).isEqualTo("weak_password");
        assertThat(accounts.count()).isZero();
    }

    @Test
    @DisplayName("registration does not start a session; story 2 owns that")
    void doesNotLogTheVisitorIn() {
        register("alice", "alice@example.com", PASSWORD);

        assertThat(client.sessionCookie()).isNull();
        assertThat(client.get("/api/hello").status()).isEqualTo(401);
    }

    private ApiClient.Response register(String username, String email, String password) {
        return client.post(
                "/api/auth/register",
                Map.of("username", username, "email", email, "password", password));
    }
}
