package com.sgtechstack.helloworldauthapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtechstack.helloworldauthapp.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Applies the request-rate limits declared in {@code app.security.rate-limits}
 * to unauthenticated write endpoints.
 *
 * <p>Rate limiting previously covered {@code POST /api/auth/login} and nothing
 * else, which left registration and both password-reset endpoints open. Each of
 * those runs a BCrypt hash, so an unauthenticated caller could spend server CPU
 * at negligible cost to themselves, fill the users table, or issue unlimited
 * reset tokens for somebody else's address.
 *
 * <p>Rules live in YAML alongside the URL guard matrix, following the same
 * configuration-owned principle: changing what is limited is a config change
 * reviewable in a diff, not a code change.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LIMITED_MESSAGE = "Too many requests. Try again later.";

    private final RequestRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;
    private final List<SecurityProperties.RateLimit> rules;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(
            RequestRateLimiter rateLimiter,
            ClientIpResolver clientIpResolver,
            ObjectMapper objectMapper,
            List<SecurityProperties.RateLimit> rules
    ) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        SecurityProperties.RateLimit rule = matchingRule(request);

        if (rule != null) {
            String clientIp = clientIpResolver.resolve(request);

            if (rateLimiter.exceedsLimit(rule.name(), clientIp, rule.maxRequests(), rule.window())) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(LIMITED_MESSAGE)));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private SecurityProperties.RateLimit matchingRule(HttpServletRequest request) {
        for (SecurityProperties.RateLimit rule : rules) {
            if (HttpMethod.valueOf(rule.method()).matches(request.getMethod())
                    && pathMatcher.match(rule.path(), request.getRequestURI())) {
                return rule;
            }
        }
        return null;
    }
}
