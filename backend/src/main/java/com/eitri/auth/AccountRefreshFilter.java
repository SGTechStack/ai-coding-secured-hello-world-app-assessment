package com.eitri.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Makes account changes reach live sessions on their very next request, instead of at the next login.
 * For every authenticated request it reloads the account by id: a deleted or disabled account's session
 * is invalidated and the request gets {@code 401}; a changed role replaces the session's authentication,
 * so authorization never trusts stale authorities. Lock state is deliberately not rechecked.
 */
@Component
public final class AccountRefreshFilter extends OncePerRequestFilter {

    private final AccountRepository accounts;
    private final SecurityContextRepository securityContextRepository;

    AccountRefreshFilter(AccountRepository accounts, SecurityContextRepository securityContextRepository) {
        this.accounts = accounts;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            Optional<Account> account = accounts.findById(principal.accountId());
            if (account.isEmpty() || !account.get().isEnabled()) {
                invalidate(request.getSession(false));
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            Role currentRole = account.get().getRole();
            if (currentRole != principal.role()) {
                refresh(authentication, principal, currentRole, request, response);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void refresh(
            Authentication stale,
            AccountPrincipal principal,
            Role currentRole,
            HttpServletRequest request,
            HttpServletResponse response) {
        AccountPrincipal refreshed = principal.withRole(currentRole);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(refreshed, null, refreshed.getAuthorities());
        authentication.setDetails(stale.getDetails());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private static void invalidate(HttpSession session) {
        if (session == null) {
            return;
        }
        try {
            session.invalidate();
        } catch (IllegalStateException alreadyInvalidated) {
            // A concurrent request already ended it; this request is still rejected.
        }
    }
}
