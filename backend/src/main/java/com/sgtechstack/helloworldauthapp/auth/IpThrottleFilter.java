package com.sgtechstack.helloworldauthapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects login attempts from an IP that has already exceeded
 * {@link IpLoginThrottle}'s failure threshold, before the request even
 * reaches Spring Security's authentication filter. This is what makes the
 * throttle actually independent of account lockout: it runs on the source
 * IP alone, regardless of which username is being attempted.
 */
public class IpThrottleFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IpThrottleFilter.class);
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String THROTTLED_MESSAGE = "Too many failed login attempts. Try again later.";

    private final IpLoginThrottle ipLoginThrottle;
    private final ObjectMapper objectMapper;

    public IpThrottleFilter(IpLoginThrottle ipLoginThrottle, ObjectMapper objectMapper) {
        this.ipLoginThrottle = ipLoginThrottle;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        boolean isLoginAttempt = HttpMethod.POST.matches(request.getMethod())
                && LOGIN_PATH.equals(request.getRequestURI());

        if (isLoginAttempt && ipLoginThrottle.isThrottled(request.getRemoteAddr())) {
            log.info("Login throttled ip={}", request.getRemoteAddr());
            response.setStatus(429); // 429 Too Many Requests; not a named HttpServletResponse.SC_* constant
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(THROTTLED_MESSAGE)));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
