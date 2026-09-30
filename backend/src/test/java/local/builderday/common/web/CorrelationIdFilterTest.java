package local.builderday.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void should_useTraceparentTraceId_andClearMdc_afterRequest() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    var seen = new ArrayList<String>();

    filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.add(MDC.get("trace.id")));

    assertThat(seen).containsExactly("4bf92f3577b34da6a3ce929d0e0e4736");
    assertThat(MDC.get("trace.id")).isNull();
  }

  @Test
  void should_generateUuid_whenTraceparentIsMissingOrMalformed() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("traceparent", "not-a-traceparent");
    var seen = new ArrayList<String>();

    filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.add(MDC.get("trace.id")));

    assertThat(seen).singleElement().satisfies(id -> assertThat(UUID.fromString(id)).isNotNull());
    assertThat(MDC.get("trace.id")).isNull();
  }
}
