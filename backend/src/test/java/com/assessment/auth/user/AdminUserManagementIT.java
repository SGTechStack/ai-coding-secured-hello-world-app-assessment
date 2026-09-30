package com.assessment.auth.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.assessment.auth.support.AbstractIntegrationTest;
import com.assessment.auth.support.ApiClient;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Administrator user management (stories 1.14–1.20). */
class AdminUserManagementIT extends AbstractIntegrationTest {

  private ApiClient admin;

  private UUID createUser(String username, String role) {
    admin.fetchCsrf();
    ResponseEntity<String> created =
        admin.post(
            "/users",
            "{\"username\":\"" + username + "\",\"email\":\"" + username
                + "@example.com\",\"password\":\"lantern quiet field\",\"role\":\"" + role + "\"}");
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return userRepository.findByUsername(username).orElseThrow().getId();
  }

  private void loginAdmin() {
    admin = adminSession();
  }

  // ------------------------------------------------------------ 1.14: listing and detail

  @Test
  @DisplayName("the list and detail projections are different shapes, and neither leaks a hash")
  void listAndDetailProjections() {
    loginAdmin();
    UUID id = createUser("listee", Role.USER);

    String list = admin.get("/users").getBody();
    assertThat(list).contains("listee", "listee@example.com", "\"enabled\"", "\"createdAt\"");
    assertThat(list).doesNotContain("passwordHash", "password_hash");
    // The list projection is deliberately narrow: no lock state, no attempt count.
    assertThat(list).doesNotContain("failedLoginAttempts");

    String detail = admin.get("/users/" + id).getBody();
    assertThat(detail)
        .contains("\"lockedUntil\"", "\"failedLoginAttempts\"", "\"requirePasswordChange\"", "\"lastLoginAt\"");
    assertThat(detail).doesNotContain("passwordHash");
  }

  // ------------------------------------------------------------------ 1.15: create

  @Test
  @DisplayName("an administrator-created account is active and flagged for a forced change")
  void adminCreateFlagsForcedChange() {
    loginAdmin();
    UUID id = createUser("freshie", Role.USER);
    User created = userRepository.findById(id).orElseThrow();

    assertThat(created.isEnabled()).isTrue();
    assertThat(created.isRequirePasswordChange())
        .as("the new user's first action must be choosing their own password")
        .isTrue();
    // The first history row is written, so the supplied password counts as a reuse immediately.
    assertThat(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(id)).hasSize(1);
  }

  @Test
  @DisplayName("uniqueness is checked against live users and tombstones, username and email alike")
  void uniquenessCoversTombstones() {
    loginAdmin();
    UUID id = createUser("ghost", Role.USER);

    admin.fetchCsrf();
    assertThat(admin.delete("/users/" + id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // The recipe checks only username against tombstones (Priv:149), which is a bug -- Std:96
    // states email uniqueness unqualified. Both must be permanently burned.
    admin.fetchCsrf();
    ResponseEntity<String> sameUsername =
        admin.post(
            "/users",
            "{\"username\":\"ghost\",\"email\":\"other@example.com\","
                + "\"password\":\"lantern quiet field\",\"role\":\"USER\"}");
    assertThat(sameUsername.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    admin.fetchCsrf();
    ResponseEntity<String> sameEmail =
        admin.post(
            "/users",
            "{\"username\":\"other\",\"email\":\"ghost@example.com\","
                + "\"password\":\"lantern quiet field\",\"role\":\"USER\"}");
    assertThat(sameEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // ------------------------------------------------------------ 1.16: enable / disable

  @Test
  @DisplayName("disabling kills sessions; re-enabling forces a password change")
  void disableAndReEnable() {
    loginAdmin();
    UUID id = createUser("switchy", Role.USER);
    jdbcTemplate.update("update users set require_password_change = false where username = 'switchy'");

    ApiClient victim = new ApiClient(port);
    victim.login("switchy", "lantern quiet field");
    assertThat(victim.get("/hello").getStatusCode()).isEqualTo(HttpStatus.OK);

    admin.fetchCsrf();
    assertThat(admin.patch("/users/" + id + "/status", "{\"enabled\":false}").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    // The pre-existing session must die immediately, not at its next natural expiry.
    assertThat(victim.get("/hello").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(userRepository.findById(id).orElseThrow().getDisabledAt()).isNotNull();

    // A disabled account's login is the same generic 401 as any other failure (Std:85, :259).
    assertThat(new ApiClient(port).login("switchy", "lantern quiet field").getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    admin.fetchCsrf();
    admin.patch("/users/" + id + "/status", "{\"enabled\":true}");
    // Std:130.
    assertThat(userRepository.findById(id).orElseThrow().isRequirePasswordChange()).isTrue();
    assertThat(userRepository.findById(id).orElseThrow().getDisabledAt()).isNull();
  }

  // ------------------------------------------------------------------ 1.17: role change

  @Test
  @DisplayName("a role change kills the target's sessions so a demotion takes effect at once")
  void roleChangeRevokesSessions() {
    loginAdmin();
    UUID id = createUser("climber", Role.USER);
    jdbcTemplate.update("update users set require_password_change = false where username = 'climber'");

    ApiClient user = new ApiClient(port);
    user.login("climber", "lantern quiet field");
    assertThat(user.get("/users").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    admin.fetchCsrf();
    assertThat(
            admin.patch("/users/" + id + "/role", "{\"role\":\"USER_MANAGER\"}").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    // A promoted or demoted user must re-authenticate; the old session carries the old authority.
    assertThat(user.get("/users").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    ApiClient promoted = new ApiClient(port);
    promoted.login("climber", "lantern quiet field");
    assertThat(promoted.get("/users").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("GET /roles is the read-only seeded list, and roles cannot be written")
  void rolesAreReadOnly() {
    loginAdmin();
    String roles = admin.get("/roles").getBody();
    assertThat(roles).contains("USER").contains("USER_MANAGER");

    admin.fetchCsrf();
    // No write route exists at all; the terminal denyAll closes the path.
    assertThat(admin.post("/roles", "{\"name\":\"SUPERUSER\"}").getStatusCode())
        .isIn(HttpStatus.FORBIDDEN, HttpStatus.METHOD_NOT_ALLOWED, HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("a change leaving no enabled USER_MANAGER is 409 LAST_USER_MANAGER")
  void lastUserManagerIsProtected() {
    loginAdmin();
    UUID adminId = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getId();
    UUID helper = createUser("helper", Role.USER_MANAGER);

    // Demoting the helper is fine -- the bootstrap admin remains.
    admin.fetchCsrf();
    assertThat(admin.patch("/users/" + helper + "/role", "{\"role\":\"USER\"}").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    // Demoting the last one is not. 409 rather than 403: the request is authorized, the system
    // state is what forbids it.
    ApiClient second = new ApiClient(port);
    jdbcTemplate.update("update users set require_password_change = false where username = 'helper'");
    admin.fetchCsrf();
    admin.patch("/users/" + helper + "/role", "{\"role\":\"USER_MANAGER\"}");
    ApiClient helperSession = new ApiClient(port);
    helperSession.login("helper", "lantern quiet field");
    helperSession.fetchCsrf();
    // helper demotes the bootstrap admin, leaving only helper -- allowed.
    assertThat(helperSession.patch("/users/" + adminId + "/role", "{\"role\":\"USER\"}").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    // Now helper is the only enabled USER_MANAGER and cannot be disabled by anyone.
    ApiClient reAdmin = new ApiClient(port);
    jdbcTemplate.update(
        "update users set role = 'USER_MANAGER', require_password_change = false where username = ?",
        ADMIN_USERNAME);
    reAdmin.login(ADMIN_USERNAME, ADMIN_PASSWORD);
    reAdmin.fetchCsrf();
    // Demote the bootstrap admin again so helper is alone, then try to demote helper.
    jdbcTemplate.update("update users set role = 'USER' where username = ?", ADMIN_USERNAME);
    helperSession.fetchCsrf();
    ResponseEntity<String> refused =
        helperSession.patch("/users/" + helper + "/role", "{\"role\":\"USER\"}");
    assertThat(refused.getStatusCode()).isIn(HttpStatus.CONFLICT, HttpStatus.FORBIDDEN);
    if (refused.getStatusCode() == HttpStatus.CONFLICT) {
      assertThat(refused.getBody()).contains("LAST_USER_MANAGER");
    }
  }

  // ------------------------------------------------------------------ 1.18: unlock

  @Test
  @DisplayName("unlock clears locked_until and the counter, with no reason field")
  void unlockClearsLockState() {
    loginAdmin();
    UUID id = createUser("jammed", Role.USER);
    jdbcTemplate.update("update users set require_password_change = false where username = 'jammed'");

    for (int attempt = 1; attempt <= 5; attempt++) {
      new ApiClient(port).login("jammed", "wrong wrong wrong");
    }
    assertThat(userRepository.findById(id).orElseThrow().isLockedAt(clock().instant())).isTrue();

    admin.fetchCsrf();
    // No body at all: Qs:384 over :386, no mandatory justification.
    assertThat(admin.patch("/users/" + id + "/unlock", null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    User unlocked = userRepository.findById(id).orElseThrow();
    assertThat(unlocked.getLockedUntil()).isNull();
    assertThat(unlocked.getFailedLoginAttempts()).isZero();
    assertThat(new ApiClient(port).login("jammed", "lantern quiet field").getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  // ------------------------------------------------------------------ 1.19: delete

  @Test
  @DisplayName("delete writes a tombstone, removes the row, cascades history and kills sessions")
  void deleteLeavesATombstone() {
    loginAdmin();
    UUID id = createUser("doomed", Role.USER);
    jdbcTemplate.update("update users set require_password_change = false where username = 'doomed'");

    ApiClient victim = new ApiClient(port);
    victim.login("doomed", "lantern quiet field");
    assertThat(victim.get("/hello").getStatusCode()).isEqualTo(HttpStatus.OK);

    admin.fetchCsrf();
    assertThat(admin.delete("/users/" + id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(userRepository.findById(id)).isEmpty();
    assertThat(victim.get("/hello").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    // password_history cascades away with the account.
    assertThat(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(id)).isEmpty();

    DeletedUser tombstone = deletedUserRepository.findById(id).orElseThrow();
    // The SAME id, so an audit line's target_user_id stays resolvable after the account is gone.
    assertThat(tombstone.getId()).isEqualTo(id);
    assertThat(tombstone.getUsername()).isEqualTo("doomed");
    assertThat(tombstone.getEmail()).isEqualTo("doomed@example.com");
    assertThat(tombstone.getDeletedByUserId())
        .isEqualTo(userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getId());

    // The list needs no tombstone filter, because the row is genuinely gone.
    assertThat(admin.get("/users").getBody()).doesNotContain("doomed");
  }

  // ---------------------------------------------------------- the self-action guard

  @Test
  @DisplayName("an administrator cannot disable, demote, unlock, delete or reset their own account")
  void selfActionsAreRefused() {
    loginAdmin();
    UUID self = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getId();

    record Attempt(String label, String path, String body, boolean isDelete) {}
    var attempts =
        java.util.List.of(
            new Attempt("status", "/users/" + self + "/status", "{\"enabled\":false}", false),
            new Attempt("role", "/users/" + self + "/role", "{\"role\":\"USER\"}", false),
            new Attempt("unlock", "/users/" + self + "/unlock", null, false),
            // spec.md S3 lists rows 11/12/13/15; story 1.20 requires it here too, so the guard is
            // applied to resetPassword as well and both are satisfied.
            new Attempt("resetPassword", "/users/" + self + "/resetPassword", null, false),
            new Attempt("delete", "/users/" + self, null, true));

    for (Attempt attempt : attempts) {
      admin.fetchCsrf();
      ResponseEntity<String> response =
          attempt.isDelete() ? admin.delete(attempt.path()) : admin.patch(attempt.path(), attempt.body());
      assertThat(response.getStatusCode()).as("%s on self", attempt.label()).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(response.getBody()).as("%s on self", attempt.label()).contains("SELF_ACTION_NOT_ALLOWED");
    }
  }
}
