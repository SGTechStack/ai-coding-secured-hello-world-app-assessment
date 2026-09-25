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
    // Deliberately distinct from LockoutPolicy/LoginFailureHandler's wording:
    // this is a per-IP rate limit, not an account lockout, and it doesn't name
    // any account, so saying "this IP" openly can't be used to enumerate
    // accounts the way revealing account-lockout state could.
    private static final String THROTTLED_MESSAGE =
            "Too many failed login attempts from this network. Try again shortly.";

    private final IpLoginThrottle ipLoginThrottle;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    public IpThrottleFilter(
            IpLoginThrottle ipLoginThrottle,
            ObjectMapper objectMapper,
            ClientIpResolver clientIpResolver
    ) {
        this.ipLoginThrottle = ipLoginThrottle;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        boolean isLoginAttempt = HttpMethod.POST.matches(request.getMethod())
                && LOGIN_PATH.equals(request.getRequestURI());

        String clientIp = isLoginAttempt ? clientIpResolver.resolve(request) : null;

        if (isLoginAttempt && ipLoginThrottle.isThrottled(clientIp)) {
            log.info("Login throttled ip={}", clientIp);
            response.setStatus(429); // 429 Too Many Requests; not a named HttpServletResponse.SC_* constant
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(THROTTLED_MESSAGE)));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
