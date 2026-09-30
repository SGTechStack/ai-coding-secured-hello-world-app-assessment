package org.eds.demo.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

public final class WebSpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
  private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
  private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

  @Override
  public void handle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Supplier<CsrfToken> csrfToken) {

    /*
     * Always use XorCsrfTokenRequestAttributeHandler to provide BREACH protection
     * of the CsrfToken when it is rendered in the response body.
     */
    this.xor.handle(request, response, csrfToken);

    /*
     * Force the token to be loaded so the cookie is rendered.
     */
    csrfToken.get();
  }

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {

    String headerValue = request.getHeader(csrfToken.getHeaderName());

    /*
     * If the request contains a CSRF header, use the plain handler.
     * This is the SPA case where JS reads XSRF-TOKEN cookie and sends X-XSRF-TOKEN.
     */
    return StringUtils.hasText(headerValue)
        ? this.plain.resolveCsrfTokenValue(request, csrfToken)
        : this.xor.resolveCsrfTokenValue(request, csrfToken);
  }
}
