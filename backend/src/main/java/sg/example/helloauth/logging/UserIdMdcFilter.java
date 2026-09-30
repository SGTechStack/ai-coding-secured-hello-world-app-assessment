package sg.example.helloauth.logging;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import sg.example.helloauth.account.AccountPrincipal;

/**
 * Puts the logged-in Account's UUID on every log line of its requests, never its username. It
 * belongs in the security filter chain just after the security context is loaded.
 */
public final class UserIdMdcFilter extends OncePerRequestFilter {

    static final String MDC_KEY = "user.id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, principal.id().toString())) {
                chain.doFilter(request, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
