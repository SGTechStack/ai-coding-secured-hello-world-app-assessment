package org.eds.demo.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RequestTracingFilterTest {

  private final RequestTracingFilter filter = new RequestTracingFilter();

  @Mock private FilterChain filterChain;

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void usesRequestIdHeaderWhenPresent() throws Exception {
    var request = new MockHttpServletRequest();
    var existingId = "550e8400-e29b-41d4-a716-446655440000";
    request.addHeader("X-Request-Id", existingId);
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(response.getHeader("X-Request-Id")).isEqualTo(existingId);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void generatesRequestIdWhenHeaderAbsent() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(response.getHeader("X-Request-Id")).isNotBlank();
  }

  @Test
  void generatesRequestIdWhenHeaderBlank() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "  ");
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(response.getHeader("X-Request-Id")).isNotBlank();
    assertThat(response.getHeader("X-Request-Id")).isNotEqualTo("  ");
  }

  @Test
  void setsMdcRequestIdDuringFilterChain() throws Exception {
    var request = new MockHttpServletRequest();
    var traceId = "550e8400-e29b-41d4-a716-446655440001";
    request.addHeader("X-Request-Id", traceId);
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(
        request, response, (req, res) -> assertThat(MDC.get("request.id")).isEqualTo(traceId));
  }

  @Test
  void generatesRequestIdWhenHeaderIsNotAUuid() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "not-a-uuid\nERROR: injected");
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(response.getHeader("X-Request-Id"))
        .doesNotContain("injected")
        .matches("[0-9a-f\\-]{36}");
  }

  @Test
  void clearsMdcAfterFilterChain() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    assertThat(MDC.get("request.id")).isNull();
  }
}
