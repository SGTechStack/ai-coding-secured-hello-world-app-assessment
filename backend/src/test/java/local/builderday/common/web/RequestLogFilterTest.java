package local.builderday.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import local.builderday.common.audit.SessionIds;
import local.builderday.support.AuditLogCapture;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.DefaultCookieSerializer;

/** What HTTP cannot reach: the global exception handler turns controller exceptions into 500s before they escape. */
class RequestLogFilterTest {
  private final RequestLogFilter filter = new RequestLogFilter(
      Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC), new SessionIds("unit-test-secret"),
      new DefaultCookieSerializer());

  @Test
  void should_logA500FailureAtError_andRethrow_when_anExceptionEscapesTheChain() {
    var failure = new IllegalStateException("boom");
    var response = new MockHttpServletResponse();
    try (var access = new AuditLogCapture(RequestLogFilter.LOGGER)) {
      assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/api/profile"), response,
          (request, ignored) -> { throw failure; })).isSameAs(failure);

      assertThat(access.events()).hasSize(2).last().satisfies(completion -> {
        assertThat(completion.getLevel()).isEqualTo(Level.ERROR);
        assertThat(completion.getFormattedMessage()).matches("GET /api/profile 500 \\d+ms");
        assertThat(AuditLogCapture.fields(completion)).containsEntry("http.response.status_code", 500)
            .containsEntry("event.outcome", "failure");
      });
    }
  }
}
