package com.example.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Short-circuits a login POST with {@code 429 Too Many Requests} when the
 * requesting IP has already been throttled by {@link IpLoginThrottleService},
 * before {@code AuthenticationManager} (and therefore any password
 * comparison) ever runs. Scoped to the login URL only -- every other endpoint
 * is unaffected.
 */
public class IpThrottleFilter extends OncePerRequestFilter {

    private final String loginUrl;
    private final IpLoginThrottleService ipLoginThrottleService;

    public IpThrottleFilter(String loginUrl, IpLoginThrottleService ipLoginThrottleService) {
        this.loginUrl = loginUrl;
        this.ipLoginThrottleService = ipLoginThrottleService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().equals(loginUrl) && ipLoginThrottleService.isThrottled(request.getRemoteAddr())) {
            response.setStatus(429);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
