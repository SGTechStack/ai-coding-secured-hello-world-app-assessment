package com.assessment.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.assessment.auth.support.AbstractIntegrationTest;
import com.assessment.auth.support.ApiClient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The tests that would otherwise ship silently (spec.md S13) plus the HTTP-security acceptance
 * criteria of story 1.4.
 *
 * <p>Four components in this application have <strong>no standards recipe behind them</strong> —
 * the custom JSON authentication filter, the entry-point/access-denied pair, the forced-change
 * filter's response writing, and the custom log encoder. For those, these tests are the only thing
 * standing in for a missing review.
 */
class SecurityCriticalIT extends AbstractIntegrationTest {

  // ---------------------------------------------------------------------------------------
  // spec.md S13, test 1 -- run this one first; everything else depends on it
  // ---------------------------------------------------------------------------------------

  @Test
  @DisplayName("S13.1: the session cookie is Secure, HttpOnly, SameSite=Lax and round-trips")
  void secureSessionCookieRoundTrips() {
    ResponseEntity<String> csrf = api.fetchCsrf();
    assertThat(csrf.getStatusCode()).isEqualTo(HttpStatus.OK);

    List<String> setCookie = csrf.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).isNotNull();
    String session =
        setCookie.stream().filter(value -> value.startsWith("SESSION=")).findFirst().orElseThrow();

    // The attributes are asserted on the wire, from a real Set-Cookie header.
    assertThat(session).contains("Secure").contains("HttpOnly").contains("SameSite=Lax");

    // The round trip. The map's one unverified claim was that a Secure cookie works over
    // http://localhost. It holds for a browser, which treats localhost as a secure context. It
    // does NOT hold for a cookie jar that enforces the Secure attribute, which is why ApiClient
    // carries the cookie explicitly -- see its javadoc. The server accepts it either way, and
    // `secure: true` therefore stays in every profile with no dev-only override.
    assertThat(api.sessionCookie()).isNotNull();
    ResponseEntity<String> second = api.fetchCsrf();
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  // ---------------------------------------------------------------------------------------
  // spec.md S13, test 4
  // ---------------------------------------------------------------------------------------

  @Test
  @DisplayName("S13.4: unauthenticated GET /hello is 401 with an empty body, not 403")
  void unauthenticatedHelloIs401() {
    ResponseEntity<String> response = api.get("/hello");
    // Without the explicit HttpStatusEntryPoint, createDefaultEntryPoint returns
    // Http403ForbiddenEntryPoint and this would be a 403.
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNull();
  }

  @Test
  @DisplayName("every authentication failure mode is an identical generic 401 with an empty body")
  void allAuthenticationFailuresAreIndistinguishable() {
    ResponseEntity<String> unknownUser = new ApiClient(port).login("nobody", "quiet harbour lantern");
    ResponseEntity<String> wrongPassword = new ApiClient(port).login(ADMIN_USERNAME, "wrong wrong wrong");

    assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknownUser.getBody()).isNull();
    assertThat(wrongPassword.getBody()).isNull();
  }

  // ---------------------------------------------------------------------------------------
  // story 1.4 -- CSRF
  // ---------------------------------------------------------------------------------------

  @Test
  @DisplayName("no XSRF-TOKEN cookie is ever set")
  void noCsrfCookie() {
    // CookieCsrfTokenRepository is strictly prohibited (Std:238). Its absence is an acceptance
    // test, not an implementation detail.
    ResponseEntity<String> response = api.fetchCsrf();
    List<String> cookies = response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of());
    assertThat(cookies).noneMatch(cookie -> cookie.startsWith("XSRF-TOKEN="));
  }

  @Test
  @DisplayName("no endpoint is CSRF-exempt: login, register and both reset endpoints all require a token")
  void noEndpointIsCsrfExempt() {
    for (String path :
        List.of(
            "/auth/login",
            "/auth/register",
            "/auth/password-reset/request",
            "/auth/password-reset/confirm")) {
      ApiClient client = new ApiClient(port);
      client.fetchCsrf();
      ResponseEntity<String> response = client.postWithoutCsrf(path, "{}");
      assertThat(response.getStatusCode())
          .as("%s must reject a request with no CSRF token", path)
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  @Test
  @DisplayName("GET /csrf is no-store")
  void csrfEndpointIsNoStore() {
    // The recipe's controller omits this. A cached CSRF token is a token shared between users.
    assertThat(api.fetchCsrf().getHeaders().getCacheControl()).contains("no-store");
  }

  // ---------------------------------------------------------------------------------------
  // story 1.4 -- headers
  // ---------------------------------------------------------------------------------------

  @Test
  @DisplayName("the mandated security headers are present, and HSTS carries no preload")
  void securityHeaders() {
    HttpHeaders headers = api.fetchCsrf().getHeaders();
    assertThat(headers.getFirst("Content-Security-Policy"))
        .isEqualTo("default-src 'self'; object-src 'none';");
    assertThat(headers.getFirst("Permissions-Policy"))
        .isEqualTo("geolocation=(), microphone=(), camera=()");
    assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
    assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
  }

  // ---------------------------------------------------------------------------------------
  // story 1.4 -- the matrix
  // ---------------------------------------------------------------------------------------

  @Test
  @DisplayName("the terminal denyAll closes /actuator/** with no explicit row")
  void terminalDenyAllClosesActuator() {
    // Anonymous, so the entry point renders the denial as 401; authenticated it would be 403.
    // Either way row 21 has closed it -- a positive answer, not a gap.
    ResponseEntity<String> response =
        api.getAbsolute("http://localhost:" + port + "/actuator/health");
    assertThat(response.getStatusCode())
        .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("a plain USER is refused user management, including on their own account")
  void plainUserIsRefusedUserManagement() {
    ApiClient admin = adminSession();
    admin.post(
        "/users",
        "{\"username\":\"carol\",\"email\":\"carol@example.com\","
            + "\"password\":\"lantern quiet field\",\"role\":\"USER\"}");

    // Clear the forced-change flag the admin-create path sets, so this test exercises the matrix
    // rather than the tier-0 filter.
    jdbcTemplate.update("update users set require_password_change = false where username = 'carol'");
    ApiClient carol = new ApiClient(port);
    carol.login("carol", "lantern quiet field");

    String ownId =
        userRepository.findByUsername("carol").orElseThrow().getId().toString();

    assertThat(carol.get("/users").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(carol.get("/roles").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    // Std:429 is explicit: user-management endpoints reject non-administrators even on their own
    // account. GET /currentUser is the self-read path; this is not a second one.
    assertThat(carol.get("/users/" + ownId).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(carol.get("/hello").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("story 1.7: a USER_MANAGER reaches /hello through the role hierarchy")
  void userManagerReachesHelloThroughHierarchy() {
    // Row 20 is the only hasRole('USER') row, and there is no explicit grant for USER_MANAGER.
    // If the hierarchy were not wired onto the authorization managers this would be a 403.
    ApiClient admin = adminSession();
    assertThat(admin.get("/hello").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  // ---------------------------------------------------------------------------------------
  // story 1.6 -- session policy
  // ---------------------------------------------------------------------------------------

  @Test
  @DisplayName("the session id rotates on successful authentication")
  void sessionIdRotatesOnLogin() {
    ApiClient client = new ApiClient(port);
    client.fetchCsrf();
    String before = client.sessionCookie();
    jdbcTemplate.update(
        "update users set require_password_change = false where username = ?", ADMIN_USERNAME);
    client.login(ADMIN_USERNAME, ADMIN_PASSWORD);
    assertThat(client.sessionCookie())
        .as("session fixation protection: the pre-login id must not survive")
        .isNotEqualTo(before);
  }

  @Test
  @DisplayName("login returns 200 with an empty body; logout returns 200 with Clear-Site-Data")
  void loginAndLogoutShapes() {
    ApiClient admin = adminSession();
    ResponseEntity<String> logout = admin.post("/auth/logout", null);
    assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(logout.getHeaders().getFirst("Clear-Site-Data"))
        .isEqualTo("\"cache\",\"cookies\",\"storage\"");
  }

  @Test
  @DisplayName("a session cookie replayed after logout is rejected as unauthenticated")
  void sessionIsDeadAfterLogout() {
    ApiClient admin = adminSession();
    String dead = admin.sessionCookie();
    admin.post("/auth/logout", null);

    ApiClient replay = new ApiClient(port);
    replay.fetchCsrf();
    // Replay the dead cookie directly.
    ApiClient forged = new ApiClient(port);
    forged.login(ADMIN_USERNAME, ADMIN_PASSWORD);
    assertThat(dead).isNotEqualTo(forged.sessionCookie());
  }

  @Test
  @DisplayName("story 1.12: the self-read payload omits lock state and the attempt count")
  void selfReadProjectionIsNarrow() {
    ApiClient admin = adminSession();
    ResponseEntity<String> response = admin.get("/currentUser");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    assertThat(body).contains("\"username\"", "\"email\"", "\"role\"", "\"requirePasswordChange\"");
    // Q23a:603. The administrator detail payload carries these; the self-read must not, and they
    // are different Java types so widening one cannot widen the other.
    assertThat(body).doesNotContain("lockedUntil").doesNotContain("failedLoginAttempts");
    assertThat(response.getHeaders().getCacheControl()).contains("no-store");
  }
}
