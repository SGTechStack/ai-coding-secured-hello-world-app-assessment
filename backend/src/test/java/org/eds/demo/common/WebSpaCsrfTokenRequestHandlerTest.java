package org.eds.demo.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class WebSpaCsrfTokenRequestHandlerTest {

  private static final String HEADER_NAME = "X-XSRF-TOKEN";
  private static final String PARAM_NAME = "_csrf";
  private static final String TOKEN_VALUE = "test-csrf-token";

  private WebSpaCsrfTokenRequestHandler handler;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private CsrfToken csrfToken;

  @BeforeEach
  void setUp() {
    handler = new WebSpaCsrfTokenRequestHandler();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    csrfToken = new DefaultCsrfToken(HEADER_NAME, PARAM_NAME, TOKEN_VALUE);
  }

  @Test
  void handleForcesTokenToLoad() {
    @SuppressWarnings("unchecked")
    Supplier<CsrfToken> tokenSupplier = mock(Supplier.class);
    when(tokenSupplier.get()).thenReturn(csrfToken);

    handler.handle(request, response, tokenSupplier);

    verify(tokenSupplier, atLeastOnce()).get();
  }

  @Test
  void resolveCsrfTokenValueUsesRawHeaderWhenXsrfHeaderPresent() {
    request.addHeader(HEADER_NAME, TOKEN_VALUE);

    String resolved = handler.resolveCsrfTokenValue(request, csrfToken);

    // plain handler returns the raw header value directly
    assertThat(resolved).isEqualTo(TOKEN_VALUE);
  }

  @Test
  void resolveCsrfTokenValueDelegatesToXorHandlerWhenNoHeader() {
    // no X-XSRF-TOKEN header — XOR handler is used, returns null for missing/invalid token
    String resolved = handler.resolveCsrfTokenValue(request, csrfToken);

    assertThat(resolved).isNull();
  }
}
