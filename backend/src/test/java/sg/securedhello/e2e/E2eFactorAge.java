package sg.securedhello.e2e;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import sg.securedhello.mfa.TotpFactorGrant;

/**
 * Ages an account's TOTP factor, for the step-up browser test only: {@code POST /e2e/factor-age?username=<name>}
 * re-issues the {@code FACTOR_TOTP} authority in each of the account's sessions eleven minutes in the past, past the
 * mutation window (ADR-021), without moving the clock every other browser test shares. In the test sources, a servlet
 * filter ahead of the application's own chain, so nothing here reaches a production build. Answers 204, or 404 when the
 * account has no session holding the factor.
 */
final class E2eFactorAge implements Filter {

    /** Where the browser test ages the factor. */
    static final String PATH = "/e2e/factor-age";

    private static final Duration AGE = Duration.ofMinutes(11);
    private static final String CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final Clock clock;

    E2eFactorAge(FindByIndexNameSessionRepository<? extends Session> sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse out = (HttpServletResponse) response;
        if (!"POST".equals(http.getMethod())) {
            out.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        int aged = age(sessions, http.getParameter("username"));
        out.setStatus(aged > 0 ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_NOT_FOUND);
    }

    private <S extends Session> int age(FindByIndexNameSessionRepository<S> repository, String username) {
        int aged = 0;
        for (S session : repository.findByPrincipalName(username).values()) {
            SecurityContext context = session.getAttribute(CONTEXT);
            Authentication current = context == null ? null : context.getAuthentication();
            if (current == null || current.getAuthorities().stream()
                    .noneMatch(authority -> TotpFactorGrant.AUTHORITY.equals(authority.getAuthority()))) {
                continue;
            }
            List<GrantedAuthority> authorities = current.getAuthorities().stream()
                    .map(authority -> TotpFactorGrant.AUTHORITY.equals(authority.getAuthority())
                            ? FactorGrantedAuthority.withAuthority(TotpFactorGrant.AUTHORITY)
                                    .issuedAt(clock.instant().minus(AGE)).build()
                            : (GrantedAuthority) authority)
                    .toList();
            UsernamePasswordAuthenticationToken older =
                    UsernamePasswordAuthenticationToken.authenticated(current.getPrincipal(), null, authorities);
            older.setDetails(current.getDetails());
            session.setAttribute(CONTEXT, new SecurityContextImpl(older));
            repository.save(session);
            aged++;
        }
        return aged;
    }
}
