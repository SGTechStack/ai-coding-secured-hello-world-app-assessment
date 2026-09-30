package sg.example.helloauth.security;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.web.servlet.HandlerExceptionResolver;

import sg.example.helloauth.account.AccountPrincipal;
import sg.example.helloauth.api.ApiException;
import sg.example.helloauth.api.ApiExceptionHandler;
import sg.example.helloauth.audit.AuditLogger;

/**
 * Hands errors raised in the security filter chain to Spring MVC's exception handling, so
 * {@link ApiExceptionHandler} writes them as Problem Details like every other error. A 403, such
 * as a Regular user on an admin endpoint or a missing CSRF token, is audited first.
 */
final class SecurityErrorResponses implements AuthenticationEntryPoint, AccessDeniedHandler,
        SessionInformationExpiredStrategy {

    private final HandlerExceptionResolver errors;
    private final AuditLogger audit;

    SecurityErrorResponses(HandlerExceptionResolver errors, AuditLogger audit) {
        this.errors = errors;
        this.audit = audit;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
        errors.resolveException(request, response, null, ex);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) {
        audit.accessDenied(callerId(), request);
        errors.resolveException(request, response, null, ex);
    }

    /** A session ended by a newer login is simply no longer authenticated. */
    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent event) {
        errors.resolveException(event.getRequest(), event.getResponse(), null, ApiException.unauthenticated());
    }

    /** The logged-in Account, if any: a Visitor can be denied too, e.g. without a CSRF token. */
    private static UUID callerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal
                ? principal.id()
                : null;
    }
}
