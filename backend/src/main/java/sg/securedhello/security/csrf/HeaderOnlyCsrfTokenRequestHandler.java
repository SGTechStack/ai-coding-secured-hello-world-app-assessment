package sg.securedhello.security.csrf;

import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

/**
 * Resolves the CSRF token from the {@code X-CSRF-TOKEN} header only (ADR-036). The framework's handler falls back to
 * the {@code _csrf} request parameter, which would let the token travel in URLs and reach access logs. The wrapped
 * handler is {@code final} with no switch for that fallback, so this one consults it only when the header is present.
 * Masking (BREACH protection) is the wrapped handler's.
 */
public final class HeaderOnlyCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final XorCsrfTokenRequestAttributeHandler delegate = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        delegate.handle(request, response, csrfToken);
    }

    @Override
    public @Nullable String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        if (request.getHeader(csrfToken.getHeaderName()) == null) {
            return null;
        }
        return delegate.resolveCsrfTokenValue(request, csrfToken);
    }
}
