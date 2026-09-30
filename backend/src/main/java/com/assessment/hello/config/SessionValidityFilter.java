package com.assessment.hello.config;

import com.assessment.hello.domain.User;
import com.assessment.hello.repository.UserRepository;
import com.assessment.hello.service.SessionAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

/**
 * Rejects authenticated sessions that were established before the user's
 * sessionsValidFrom epoch. This is how a password reset invalidates every existing
 * session for that user without a shared/distributed session store.
 */
@Component
public class SessionValidityFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public SessionValidityFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);

        if (auth != null && auth.isAuthenticated() && session != null) {
            Object loginAtObj = session.getAttribute(SessionAuthService.LOGIN_AT_ATTR);
            if (loginAtObj instanceof Instant loginAt) {
                Optional<User> maybeUser = userRepository.findByUsername(auth.getName());
                if (maybeUser.isPresent()) {
                    User user = maybeUser.get();
                    boolean stale = loginAt.isBefore(user.getSessionsValidFrom());
                    if (stale || !user.isEnabled()) {
                        session.invalidate();
                        SecurityContextHolder.clearContext();
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session invalidated");
                        return;
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
