package com.example.auth.security;

import com.example.auth.auth.LoginRequest;
import com.example.auth.security.ratelimit.FixedWindowRateLimiter;
import com.example.auth.security.ratelimit.RateLimiters;
import com.example.auth.user.PasswordPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses {@code POST /api/auth/login}'s JSON body ({@code {"username","password"}}) instead of
 * form-urlencoded parameters, and applies the login rate limits (ADR-0005) before any password
 * comparison. Registered in place of the default {@link UsernamePasswordAuthenticationFilter}.
 *
 * <p>Limits, checked in this order:
 * <ul>
 *   <li>per source IP -- blocked once that IP has too many <em>failures</em> (password spray);
 *   <li>per (IP, username) -- blocked after 3 failures, i.e. before the account lockout at 5, so a
 *       single source can't lock someone else out (PRD Story 3);
 *   <li>per username -- every <em>attempt</em> counts (App-Standards: 10 per account per minute).
 * </ul>
 * Failures are recorded by {@link LoginAttemptListener}; this filter only reads the failure-based
 * limiters.
 */
public class JsonUsernamePasswordAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private static final String PARSED_BODY_ATTRIBUTE =
            JsonUsernamePasswordAuthenticationFilter.class.getName() + ".PARSED_BODY";
    private static final int MAX_USERNAME_LENGTH = 64;

    private final ObjectMapper objectMapper;
    private final RateLimiters rateLimiters;

    public JsonUsernamePasswordAuthenticationFilter(ObjectMapper objectMapper, RateLimiters rateLimiters) {
        this.objectMapper = objectMapper;
        this.rateLimiters = rateLimiters;
        // loginProcessingUrl-equivalent: restrict this filter to POST /api/auth/login.
        setFilterProcessesUrl("/api/auth/login");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        LoginRequest body = parseBody(request);
        String ip = request.getRemoteAddr();
        String usernameKey = limiterKey(body.username());

        rejectIfBlocked(rateLimiters.loginIp(), rateLimiters.loginIp().check(ip));
        rejectIfBlocked(rateLimiters.loginIpUsername(), rateLimiters.loginIpUsername().check(RateLimiters.ipUsernameKey(ip, usernameKey)));
        rejectIfBlocked(rateLimiters.loginUsername(), rateLimiters.loginUsername().tryAcquire(usernameKey));

        // Inputs that can never be valid credentials: rejected without a lookup. An over-long
        // password would otherwise make BCrypt throw (-> an internal error rather than a 401).
        if (body.username() == null
                || body.username().length() > MAX_USERNAME_LENGTH
                || PasswordPolicy.exceedsMaxBytes(body.password())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return super.attemptAuthentication(request, response);
    }

    /** Trimmed (as the parent filter trims) and case-folded so "Alice" and "alice " share one bucket. */
    static String limiterKey(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static void rejectIfBlocked(FixedWindowRateLimiter limiter, FixedWindowRateLimiter.Decision decision) {
        if (decision.blocked()) {
            throw new LoginRateLimitedException(limiter.name(), decision.retryAfter());
        }
    }

    @Override
    protected String obtainUsername(HttpServletRequest request) {
        return parseBody(request).username();
    }

    @Override
    protected String obtainPassword(HttpServletRequest request) {
        return parseBody(request).password();
    }

    private LoginRequest parseBody(HttpServletRequest request) {
        LoginRequest cached = (LoginRequest) request.getAttribute(PARSED_BODY_ATTRIBUTE);
        if (cached != null) {
            return cached;
        }

        LoginRequest parsed;
        try {
            parsed = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
        } catch (JacksonException | java.io.IOException ex) {
            // Malformed/empty body: fall through with blank credentials so
            // authentication fails the normal (generic) way rather than 500ing.
            parsed = new LoginRequest(null, null);
        }
        if (parsed == null) {
            parsed = new LoginRequest(null, null);
        }
        request.setAttribute(PARSED_BODY_ATTRIBUTE, parsed);
        return parsed;
    }
}
