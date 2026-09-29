package com.example.auth.login;

import com.example.auth.audit.AuditService;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the login flow: IP-throttle guard, authentication, and
 * lockout state maintenance. All security-relevant audit events for login
 * (success, failure, account locked, IP throttled) are emitted here.
 *
 * Session-fixation protection and SecurityContext persistence remain in
 * AuthController because they require HttpServletRequest/Response.
 */
@Service
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final LockoutService lockoutService;
    private final IpThrottleService ipThrottleService;
    private final AuditService audit;

    public LoginService(AuthenticationManager authenticationManager,
                        LockoutService lockoutService,
                        IpThrottleService ipThrottleService,
                        AuditService audit) {
        this.authenticationManager = authenticationManager;
        this.lockoutService = lockoutService;
        this.ipThrottleService = ipThrottleService;
        this.audit = audit;
    }

    /**
     * Authenticates the given credentials.
     *
     * @throws IpThrottledException   if the source IP has exceeded the rate limit
     * @throws AuthenticationException if credentials are wrong, account is locked/disabled,
     *                                  or any other Spring Security authentication failure
     */
    public Authentication authenticate(String username, String password, String ip) {
        if (ipThrottleService.isThrottled(ip)) {
            audit.ipThrottled(ip);
            throw new IpThrottledException(ip);
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password));

            lockoutService.onSuccess(username);
            audit.loginSuccess(username, ip);
            return auth;

        } catch (AuthenticationException ex) {
            // Record the failure under a DB write lock, then propagate the
            // original exception so the caller returns the same generic 401
            // regardless of the cause (enumeration resistance).
            ipThrottleService.recordFailure(ip);
            lockoutService.onFailure(username);
            audit.loginFailure(username, ip);
            throw ex;
        }
    }
}
