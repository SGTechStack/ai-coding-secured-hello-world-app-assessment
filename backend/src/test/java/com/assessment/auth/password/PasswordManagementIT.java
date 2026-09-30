package com.assessment.auth.password;

import static org.assertj.core.api.Assertions.assertThat;

import com.assessment.auth.support.AbstractIntegrationTest;
import com.assessment.auth.support.ApiClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Password policy, history, forced change and reset (stories 1.8, 1.11, 1.13). */
class PasswordManagementIT extends AbstractIntegrationTest {

  private ApiClient register(String username, String password) {
    ApiClient client = new ApiClient(port);
    client.fetchCsrf();
    client.post(
        "/auth/register",
        "{\"username\":\"" + username + "\",\"email\":\"" + username
            + "@example.com\",\"password\":\"" + password + "\"}");
    ApiClient session = new ApiClient(port);
    session.login(username, password);
    return session;
  }

  private ResponseEntity<String> tryRegister(String username, String password) {
    ApiClient client = new ApiClient(port);
    client.fetchCsrf();
    return client.post(
        "/auth/register",
        "{\"username\":\"" + username + "\",\"email\":\"" + username
            + "@example.com\",\"password\":\"" + password + "\"}");
  }

  // ------------------------------------------------------------------ story 1.8: the policy

  @Test
  @DisplayName("72 characters is accepted and 73 is a validation error, never a 500")
  void bcryptBoundaryIsAValidationError() {
    // 72 is a hard BCrypt limit in Spring Security 7.0.6 -- the encoder THROWS above it. A larger
    // cap (Questions.md:296 recommends 128; the recipe says 1024) turns a long passphrase into a
    // 500. ASCII-only makes a 72-character cap an exact byte cap.
    String seventyTwo = "a".repeat(72);
    String seventyThree = "a".repeat(73);

    assertThat(tryRegister("boundary72", seventyTwo).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);

    ResponseEntity<String> tooLong = tryRegister("boundary73", seventyThree);
    assertThat(tooLong.getStatusCode())
        .as("must be a 400, not a 500 from BCrypt throwing")
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(tooLong.getBody()).contains("VALIDATION_FAILED");
  }

  @Test
  @DisplayName("no composition rules: a 12-character all-lowercase passphrase with spaces is accepted")
  void noCompositionRules() {
    // Priv:109's regex is recipe-only, and the recipe's own note says to remove it.
    assertThat(tryRegister("plainpass", "aa bb cc ddd").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("the denylist, the username and the email local part are all rejected")
  void contextualRejections() {
    assertThat(tryRegister("commonpw", "passwordpassword").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    // Contains the username.
    assertThat(tryRegister("marigold", "marigold lantern").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("history blocks four values: the current password plus the three previous")
  void historyBlocksFourValues() {
    // The literal reading of Std:355. Self-Service:269-272's procedure assumes a different depth
    // and is wrong for this application.
    String first = "first lantern pass";
    ApiClient session = register("histuser", first);

    List<String> chain = List.of("second lantern pass", "third lantern pass", "fourth lantern pass");
    String current = first;
    for (String next : chain) {
      session.fetchCsrf();
      assertThat(
              session
                  .patch(
                      "/currentUser/changePassword",
                      "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}")
                  .getStatusCode())
          .isEqualTo(HttpStatus.NO_CONTENT);
      current = next;
      // Each change kills every session, so log back in.
      session = new ApiClient(port);
      session.login("histuser", current);
    }

    // `first` is now the fourth-most-recent value and must still be blocked.
    session.fetchCsrf();
    ResponseEntity<String> reuse =
        session.patch(
            "/currentUser/changePassword",
            "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + first + "\"}");
    assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(reuse.getBody()).contains("VALIDATION_FAILED");
  }

  @Test
  @DisplayName("registration writes the account's first password-history row")
  void registrationWritesHistory() {
    register("histfirst", "lantern quiet field");
    var user = userRepository.findByUsername("histfirst").orElseThrow();
    assertThat(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
        .hasSize(1);
  }

  // ------------------------------------------------------- story 1.11: self-service change

  @Test
  @DisplayName("a wrong current password is 400 CURRENT_PASSWORD_INVALID, never 401 or 403")
  void wrongCurrentPassword() {
    // 401 here would make the SPA's interceptor log the user out over a typo.
    ApiClient session = register("typouser", "lantern quiet field");
    session.fetchCsrf();
    ResponseEntity<String> response =
        session.patch(
            "/currentUser/changePassword",
            "{\"currentPassword\":\"not the password\",\"newPassword\":\"amber tunnel window\"}");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("CURRENT_PASSWORD_INVALID");
  }

  @Test
  @DisplayName("a successful change invalidates every session including the caller's own")
  void changeKillsAllSessions() {
    ApiClient session = register("killer", "lantern quiet field");
    assertThat(session.get("/currentUser").getStatusCode()).isEqualTo(HttpStatus.OK);

    session.fetchCsrf();
    assertThat(
            session
                .patch(
                    "/currentUser/changePassword",
                    "{\"currentPassword\":\"lantern quiet field\",\"newPassword\":\"amber tunnel window\"}")
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    // This is why the first boot is three steps, and why the SPA treats a change as a logout.
    assertThat(session.get("/currentUser").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("the forced-change filter allows exactly four paths, logout among them")
  void forcedChangeAllowsFourPaths() {
    ApiClient admin = new ApiClient(port);
    admin.login(ADMIN_USERNAME, ADMIN_PASSWORD); // still flagged by resetAdmin()

    assertThat(admin.get("/csrf").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(admin.get("/currentUser").getStatusCode()).isEqualTo(HttpStatus.OK);
    // Logout is OUR addition to the recipe's three: without it a flagged user cannot end their
    // own session.
    assertThat(admin.post("/auth/logout", null).getStatusCode()).isEqualTo(HttpStatus.OK);

    ApiClient again = new ApiClient(port);
    again.login(ADMIN_USERNAME, ADMIN_PASSWORD);
    ResponseEntity<String> blocked = again.get("/users");
    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(blocked.getBody()).contains("PASSWORD_CHANGE_REQUIRED");
  }

  // ------------------------------------------------------------- story 1.13: reset by email

  @Test
  @DisplayName("a reset request is an identical generic response whether or not the email exists")
  void resetRequestIsEnumerationResistant() {
    register("resetme", "lantern quiet field");

    ApiClient known = new ApiClient(port);
    known.fetchCsrf();
    ResponseEntity<String> knownResponse =
        known.post("/auth/password-reset/request", "{\"email\":\"resetme@example.com\"}");

    ApiClient unknown = new ApiClient(port);
    unknown.fetchCsrf();
    ResponseEntity<String> unknownResponse =
        unknown.post("/auth/password-reset/request", "{\"email\":\"nobody@example.com\"}");

    assertThat(knownResponse.getStatusCode()).isEqualTo(unknownResponse.getStatusCode());
    assertThat(knownResponse.getBody()).isEqualTo(unknownResponse.getBody());
  }

  @Test
  @DisplayName("a token is 32 alphanumerics, stored only as an unsalted SHA-256, valid 30 minutes")
  void tokenShapeAndExpiry() {
    ApiClient admin = adminSession();
    register("tokenuser", "lantern quiet field");
    String targetId = userRepository.findByUsername("tokenuser").orElseThrow().getId().toString();

    admin.fetchCsrf();
    ResponseEntity<String> issued = admin.patch("/users/" + targetId + "/resetPassword", null);
    assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
    String token = issued.getBody().replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    assertThat(token).hasSize(32).matches("[A-Za-z0-9]{32}");

    var stored = passwordResetTokenRepository.findAll().getFirst();
    // Unsalted, so the hash can BE the lookup key (Std:66). Never the plaintext.
    assertThat(stored.getTokenHash()).isEqualTo(PasswordResetService.sha256(token));
    assertThat(stored.getTokenHash()).isNotEqualTo(token);
    assertThat(stored.getExpiresAt()).isEqualTo(clock().instant().plus(Duration.ofMinutes(30)));
  }

  @Test
  @DisplayName("issuing a new token deletes the prior unused one")
  void issuingSupersedes() {
    ApiClient admin = adminSession();
    register("supersede", "lantern quiet field");
    String id = userRepository.findByUsername("supersede").orElseThrow().getId().toString();

    admin.fetchCsrf();
    String first =
        admin
            .patch("/users/" + id + "/resetPassword", null)
            .getBody()
            .replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    admin.fetchCsrf();
    admin.patch("/users/" + id + "/resetPassword", null);

    // Std:112. Only the most recently issued token is ever valid, which is also what makes
    // `used_at` mean exactly "redeemed".
    assertThat(passwordResetTokenRepository.findAll()).hasSize(1);
    ApiClient client = new ApiClient(port);
    client.fetchCsrf();
    ResponseEntity<String> confirm =
        client.post(
            "/auth/password-reset/confirm",
            "{\"token\":\"" + first + "\",\"newPassword\":\"amber tunnel window\"}");
    assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("expired and already-used tokens produce one merged 400 that cannot tell them apart")
  void expiredAndUsedAreMerged() {
    ApiClient admin = adminSession();
    register("merged", "lantern quiet field");
    String id = userRepository.findByUsername("merged").orElseThrow().getId().toString();

    admin.fetchCsrf();
    String token =
        admin
            .patch("/users/" + id + "/resetPassword", null)
            .getBody()
            .replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    // Expired, by advancing the clock rather than waiting half an hour.
    clock().advance(Duration.ofMinutes(31));
    ApiClient client = new ApiClient(port);
    client.fetchCsrf();
    ResponseEntity<String> expired =
        client.post(
            "/auth/password-reset/confirm",
            "{\"token\":\"" + token + "\",\"newPassword\":\"amber tunnel window\"}");

    ApiClient other = new ApiClient(port);
    other.fetchCsrf();
    ResponseEntity<String> nonsense =
        other.post(
            "/auth/password-reset/confirm",
            "{\"token\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"newPassword\":\"amber tunnel window\"}");

    // Std:261: distinguishing them tells an attacker they guessed a real token.
    assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(nonsense.getStatusCode()).isEqualTo(expired.getStatusCode());
    assertThat(nonsense.getBody()).isEqualTo(expired.getBody());
    assertThat(expired.getBody()).contains("RESET_TOKEN_INVALID");
  }

  @Test
  @DisplayName("confirm sets the new password, clears the flag and kills every session")
  void confirmCompletesTheFlow() {
    ApiClient admin = adminSession();
    register("confirmer", "lantern quiet field");
    var user = userRepository.findByUsername("confirmer").orElseThrow();

    admin.fetchCsrf();
    String token =
        admin
            .patch("/users/" + user.getId() + "/resetPassword", null)
            .getBody()
            .replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    ApiClient client = new ApiClient(port);
    client.fetchCsrf();
    assertThat(
            client
                .post(
                    "/auth/password-reset/confirm",
                    "{\"token\":\"" + token + "\",\"newPassword\":\"amber tunnel window\"}")
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(userRepository.findByUsername("confirmer").orElseThrow().isRequirePasswordChange())
        .isFalse();
    assertThat(new ApiClient(port).login("confirmer", "amber tunnel window").getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(new ApiClient(port).login("confirmer", "lantern quiet field").getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("an administrator-initiated reset does not clear an account lock")
  void resetDoesNotUnlock() {
    // Std:131 beats Priv:441-442. The dedicated unlock endpoint is the only remedy.
    ApiClient admin = adminSession();
    register("lockedreset", "lantern quiet field");
    for (int attempt = 1; attempt <= 5; attempt++) {
      new ApiClient(port).login("lockedreset", "wrong wrong wrong");
    }
    var user = userRepository.findByUsername("lockedreset").orElseThrow();
    assertThat(user.isLockedAt(clock().instant())).isTrue();

    admin.fetchCsrf();
    admin.patch("/users/" + user.getId() + "/resetPassword", null);

    assertThat(
            userRepository
                .findByUsername("lockedreset")
                .orElseThrow()
                .isLockedAt(clock().instant()))
        .as("a reset must not lift the lock")
        .isTrue();
  }
}
