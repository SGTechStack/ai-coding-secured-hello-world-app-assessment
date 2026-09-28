package com.example.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * The standard fix (from Spring Security's own SPA-CSRF reference docs) for
 * combining {@code CookieCsrfTokenRepository} with an SPA.
 *
 * <p>The default {@link XorCsrfTokenRequestAttributeHandler} masks (XORs) the
 * token it exposes for HTML form rendering, then expects a submitted value to
 * be unmasked the same way -- that's fine for a server-rendered {@code
 * <input type="hidden">}, but an SPA reads the token straight from the {@code
 * XSRF-TOKEN} cookie (raw, unmasked) and sends it back verbatim as the {@code
 * X-XSRF-TOKEN} header. Without this override, that raw value fails the XOR
 * handler's unmask-and-compare, and every CSRF-protected request gets a 403
 * regardless of how correct the token looks.
 */
public final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    private final CsrfTokenRequestAttributeHandler delegate = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.delegate.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return StringUtils.hasText(headerValue) ? headerValue : super.resolveCsrfTokenValue(request, csrfToken);
    }
}
