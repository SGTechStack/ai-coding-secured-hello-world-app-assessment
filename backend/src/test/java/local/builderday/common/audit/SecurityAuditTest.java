package local.builderday.common.audit;

import static local.builderday.support.AuditLogCapture.fields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import local.builderday.common.audit.SecurityAudit.Event;
import local.builderday.common.audit.SecurityAudit.Outcome;
import local.builderday.support.AuditLogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class SecurityAuditTest {
  private AuditLogCapture capture;

  @BeforeEach
  void setUp() {
    capture = new AuditLogCapture(SecurityAudit.LOGGER);
    // The key is process-wide: install the one the test application context derives, so later tests still agree.
    SessionIds.install(new SessionIds("test-only-h2-password"));
    MDC.put("trace.id", "4bf92f3577b34da6a3ce929d0e0e4736");
  }

  @AfterEach
  void tearDown() {
    capture.close();
    MDC.remove("trace.id");
  }

  @Test
  void should_emitSharedEcsFieldsAndExtras_when_requestEventIsRecorded() {
    var request = request();
    var userId = UUID.randomUUID();

    SecurityAudit.record(request, new Event("user-login", "authentication", "start", Outcome.SUCCESS, "success", userId,
        Map.of("error.code", List.of("X"))));

    assertThat(capture.events()).singleElement().satisfies(event -> {
      assertThat(event.getLevel()).isEqualTo(Level.INFO);
      assertThat(fields(event)).containsEntry("event.action", "user-login")
          .containsEntry("event.category", List.of("authentication"))
          .containsEntry("event.type", List.of("start"))
          .containsEntry("event.outcome", "success")
          .containsEntry("event.reason", "success")
          .containsEntry("user.id", userId.toString())
          .containsEntry("trace.id", "4bf92f3577b34da6a3ce929d0e0e4736")
          .containsEntry("source.ip", "203.0.113.8")
          .containsEntry("url.path", "/api/auth/login")
          .containsEntry("http.request.method", "POST")
          .containsEntry("error.code", List.of("X"))
          .doesNotContainKey("session.hash");
    });
  }

  @Test
  void should_levelByOutcome_andOmitUnknownIdentityAndRequestFields() {
    SecurityAudit.record(null, new Event("a", "iam", "change", Outcome.FAILURE, "rejected", null));
    SecurityAudit.record(null, new Event("a", "iam", "change", Outcome.ERROR, "system_error", null));

    assertThat(capture.events()).extracting(event -> event.getLevel()).containsExactly(Level.WARN, Level.ERROR);
    assertThat(capture.events()).allSatisfy(event -> assertThat(fields(event))
        .containsEntry("event.outcome", "failure").doesNotContainKeys("user.id", "source.ip", "url.path"));
  }

  @Test
  void should_logOnlyTheSessionDigest_when_requestHasASession() {
    var request = request();
    var session = mock(HttpSession.class);
    when(request.getSession(false)).thenReturn(session);
    when(session.getId()).thenReturn("raw-bearer-session-id");

    SecurityAudit.record(request, new Event("a", "iam", "change", Outcome.SUCCESS, "success", null));

    assertThat(capture.events()).singleElement().satisfies(event -> {
      assertThat((String) fields(event).get("session.hash")).matches("[0-9a-f]{64}");
      assertThat(event.getFormattedMessage() + fields(event)).doesNotContain("raw-bearer-session-id");
    });
  }

  @Test
  void should_neverThrow_when_theRequestCannotBeRead() {
    var failing = mock(HttpServletRequest.class);
    when(failing.getRequestURI()).thenThrow(new IllegalStateException("request unavailable"));

    assertThatCode(() -> SecurityAudit.record(failing, new Event("a", "iam", "change", Outcome.FAILURE, "x", null)))
        .doesNotThrowAnyException();
    assertThat(capture.events()).isEmpty();
  }

  private static HttpServletRequest request() {
    var request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("203.0.113.8");
    when(request.getRequestURI()).thenReturn("/api/auth/login");
    when(request.getMethod()).thenReturn("POST");
    return request;
  }
}
