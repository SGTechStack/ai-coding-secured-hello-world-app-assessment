package com.assessment.securedhelloworld.config;

import com.assessment.securedhelloworld.auth.AppUserDetails;
import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Blocks every authenticated endpoint except password-change and logout
 * while the current user's {@code forcePasswordChange} flag is set
 * (IM8 ac-6: default/temporary credentials, e.g. the bootstrap admin
 * account, must be changed before any other action is permitted).
 *
 * <p>Re-reads the flag from the database on every request rather than
 * trusting the session-cached {@link AppUserDetails} snapshot, so a
 * password change takes effect immediately within the same session
 * without requiring re-login.
 */
public class ForcePasswordChangeFilter extends OncePerRequestFilter {

    private static final Set<String> ALWAYS_ALLOWED_PATHS = Set.of(
            "/api/auth/change-password", "/api/logout", "/api/csrf");

    private final UserRepository userRepository;

    public ForcePasswordChangeFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUserDetails principal)
                || ALWAYS_ALLOWED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        User current = userRepository.findById(principal.getUser().getId()).orElse(null);
        if (current != null && current.isForcePasswordChange()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"PASSWORD_CHANGE_REQUIRED\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
