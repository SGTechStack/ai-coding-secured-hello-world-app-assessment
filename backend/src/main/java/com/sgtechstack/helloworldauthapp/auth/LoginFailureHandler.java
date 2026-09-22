package com.sgtechstack.helloworldauthapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;

/**
 * On failed login: increments the failed-attempt counter for the targeted
 * account (if it exists), locks the account once {@link LockoutPolicy}'s
 * threshold is reached, and always responds with the same generic
 * message, so a caller cannot distinguish "wrong password", "no such
 * username", or "account is locked" — the enumeration-resistance
 * requirement extends to lockout state too.
 *
 * Also feeds {@link IpLoginThrottle}: every failed attempt, regardless of
 * whether the username exists or the account is already locked, counts
 * against the source IP. That's a separate counter from the per-account
 * one above, keyed by IP instead of username, so it engages independently
 * of any single account's lockout state.
 */
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);
    private static final String GENERIC_MESSAGE = "Invalid username or password";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final LockoutPolicy lockoutPolicy;
    private final IpLoginThrottle ipLoginThrottle;

    public LoginFailureHandler(
            UserRepository userRepository,
            ObjectMapper objectMapper,
            LockoutPolicy lockoutPolicy,
            IpLoginThrottle ipLoginThrottle
    ) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.lockoutPolicy = lockoutPolicy;
        this.ipLoginThrottle = ipLoginThrottle;
    }

    @Override
    @Transactional
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        String username = request.getParameter("username");
        boolean alreadyLocked = exception instanceof LockedException;

        if (username != null && !alreadyLocked) {
            // An attempt against an already-locked account (wrong or
            // correct password, doesn't matter) must not bump the counter
            // or extend the lockout further — it's simply rejected as-is
            // until the existing cooldown expires.
            userRepository.findByUsernameIgnoreCase(username).ifPresent(this::recordAccountFailure);
        }

        ipLoginThrottle.recordFailure(request.getRemoteAddr());

        log.info("Login failed username={} accountLocked={}", username, alreadyLocked);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(GENERIC_MESSAGE)));
    }

    private void recordAccountFailure(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (lockoutPolicy.shouldLock(attempts)) {
            Instant lockedUntil = Instant.now().plus(LockoutPolicy.LOCKOUT_DURATION);
            user.setLockedUntil(lockedUntil);
            log.info("Account locked username={} lockedUntil={}", user.getUsername(), lockedUntil);
        }

        userRepository.save(user);
    }
}
