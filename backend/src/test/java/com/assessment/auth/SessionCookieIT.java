package com.assessment.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.assessment.auth.audit.AuditLogger;
import com.assessment.auth.support.AbstractIntegrationTest;
import com.assessment.auth.support.ApiClient;
import com.assessment.auth.user.User;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

/**
 * The session-cookie and session-policy rows that {@code SecurityCriticalIT} leaves open
 * (story 1.6, spec.md S13 test 1).
 *
 * <p>Three of these could not be written without two independent cookie jars, which is the reason
 * they live here rather than being folded in: the concurrent-session limit needs two live sessions
 * at once, the replay test needs a cookie value that survives the jar that owned it, and the absolute
 * timeout needs a session that outlives a clock advance.
 */
class SessionCookieIT extends AbstractIntegrationTest {

  private ApiClient registeredSession(String username) {
    ApiClient registrar = new ApiClient(port);
    registrar.fetchCsrf();
    ResponseEntity<String> created =
        registrar.post(
            "/auth/register",
            "{\"username\":\""
                + username
                + "\",\"email\":\""
                + username
                + "@example.com\",\"password\":\"lantern quiet field\"}");
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    ApiClient session = new ApiClient(port);
    assertThat(session.login(username, "lantern quiet field").getStatusCode())
        .isEqualTo(HttpStatus.OK);
    return session;
  }

  @Test
  @DisplayName("the login Set-Cookie carries Path=/ as well as Secure, HttpOnly and SameSite=Lax")
  void sessionCookieCarriesTheConfiguredAttributes() {
    ApiClient client = new ApiClient(port);
    client.fetchCsrf();

    String setCookie = client.lastSetCookie("SESSION");
    assertThat(setCookie)
        .withFailMessage("GET /csrf emitted no SESSION cookie at all: %s", client.lastSetCookies())
        .isNotNull();
    // Path matters as much as the rest: logout's deletion cookie has to match every attribute or a
    // browser ignores it, which is the defect ticket 13 flagged.
    assertThat(setCookie).contains("Path=/");
    assertThat(setCookie).contains("Secure").contains("HttpOnly").contains("SameSite=Lax");
  }

  @Test
  @DisplayName("a cookie replayed after logout is rejected, and logout deletes it client-side too")
  void replayedPostLogoutCookieIsRejected() {
    ApiClient session = registeredSession("replayer");
    String liveCookie = session.sessionCookie();
    assertThat(session.get("/hello").getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> logout = session.post("/auth/logout", null);
    assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(logout.getHeaders().getFirst("Clear-Site-Data"))
        .isEqualTo("\"cache\",\"cookies\",\"storage\"");

    // A fresh jar carrying the exact value that was live a moment ago. Asserting on the server's
    // answer rather than on the cookie having changed: only this distinguishes a session that was
    // genuinely invalidated from one whose cookie was merely replaced.
    ResponseEntity<String> replay =
        new ApiClient(port).withSessionCookie(liveCookie).get("/hello");
    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("a second login expires the first session with a 401 and an empty body")
  void aSecondLoginExpiresTheFirstSession() {
    ApiClient first = registeredSession("concurrent");
    assertThat(first.get("/hello").getStatusCode()).isEqualTo(HttpStatus.OK);

    ApiClient second = new ApiClient(port);
    assertThat(second.login("concurrent", "lantern quiet field").getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // Std:409 requires the limit to hold across requests AND restarts, which is why the registry is
    // Spring-Session-backed rather than SessionRegistryImpl -- the in-memory one would reset the limit
    // on every restart and nothing visible would break.
    assertThat(second.get("/hello").getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> displaced = first.get("/hello");

    // The STATUS and the BODY, not just "rejected". With no explicit expiredSessionStrategy, Spring
    // Security installs ResponseBodySessionInformationExpiredStrategy: it really does kill the session,
    // but it answers 200 carrying a plain-text sentence. The SPA would then receive prose where it
    // expected JSON, never see a 401, and never run the interceptor that logs the user out (Std:438).
    // Asserting only "not OK" would pass against that behaviour, which is why both halves are here.
    assertThat(displaced.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(displaced.getBody())
        .withFailMessage("a displaced session must answer like every other 401: no body at all")
        .isNull();
  }

  @Test
  @DisplayName("a displaced session emits CONCURRENT_SESSION_EXPIRED naming the account by id")
  void displacementIsAudited() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger auditLogger = context.getLogger(AuditLogger.AUDIT_LOGGER_NAME);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.setContext(context);
    appender.start();
    auditLogger.addAppender(appender);
    try {
      ApiClient first = registeredSession("audited");
      User account = userRepository.findByUsername("audited").orElseThrow();
      new ApiClient(port).login("audited", "lantern quiet field");
      first.get("/hello");

      ILoggingEvent emitted =
          appender.list.stream()
              .filter(event -> "CONCURRENT_SESSION_EXPIRED".equals(event.getMessage()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("no CONCURRENT_SESSION_EXPIRED event was emitted"));

      Map<String, String> fields =
          emitted.getKeyValuePairs().stream()
              .collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value), (a, b) -> a));

      // The id specifically. SpringSessionBackedSessionRegistry hands the strategy the principal NAME,
      // so a plausible `instanceof AuthenticatedUser` branch never matches and this field silently
      // disappears -- and a username could not be substituted, because user.id is the only identity
      // this application writes to a log line (spec.md S11).
      assertThat(fields.get("user.id")).isEqualTo(account.getId().toString());
      assertThat(fields.get("event.outcome")).isEqualTo("failure");
      assertThat(fields.get("source.ip")).isNotBlank();
      // And the username is not in the line anywhere, by any route.
      assertThat(emitted.getFormattedMessage() + fields).doesNotContain("audited");
    } finally {
      auditLogger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  @DisplayName("the 8-hour absolute timeout ends a session that is still actively in use")
  void absoluteTimeoutEndsAnActiveSession() {
    ApiClient session = registeredSession("longlived");
    assertThat(session.get("/hello").getStatusCode()).isEqualTo(HttpStatus.OK);

    // The absolute timeout has no Spring configuration key -- AbsoluteSessionTimeoutFilter enforces
    // it against the injectable Clock, which is the only reason it is testable at all. The idle
    // timeout is a different mechanism (Spring Session reads System.currentTimeMillis) and is
    // asserted as configuration rather than behaviour.
    clock().advance(Duration.ofHours(8).plusMinutes(1));

    assertThat(session.get("/hello").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
