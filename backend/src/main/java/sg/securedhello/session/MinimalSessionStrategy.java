package sg.securedhello.session;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.stereotype.Component;

import sg.securedhello.security.csrf.HeaderOnlyCsrfTokenRequestHandler;
import sg.securedhello.security.csrf.SessionOnlyCsrfTokenRepository;

/**
 * The <em>minimal</em> session strategy (ADR-038), for a credential change and, later, factor grants: a new session
 * id (ASVS 7.2.4), a new CSRF token, and an explicit save of the security context. It never stamps
 * {@code AUTH_INSTANT}, so the absolute lifetime keeps running from sign-in; only the login composite stamps it.
 */
@Component
public class MinimalSessionStrategy implements SessionAuthenticationStrategy {

    private final SessionAuthenticationStrategy rotation;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public MinimalSessionStrategy() {
        // The chain's token store and handler are stateless; these behave exactly as the chain's own.
        CsrfAuthenticationStrategy csrf = new CsrfAuthenticationStrategy(new SessionOnlyCsrfTokenRepository());
        csrf.setRequestHandler(new HeaderOnlyCsrfTokenRequestHandler());
        this.rotation = new CompositeSessionAuthenticationStrategy(
                List.of(new ChangeSessionIdAuthenticationStrategy(), csrf));
    }

    @Override
    public void onAuthentication(Authentication authentication, HttpServletRequest request,
            HttpServletResponse response) {
        rotation.onAuthentication(authentication, request, response);
        contexts.saveContext(SecurityContextHolder.getContext(), request, response);
    }
}
