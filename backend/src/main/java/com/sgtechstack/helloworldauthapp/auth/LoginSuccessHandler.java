package com.sgtechstack.helloworldauthapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * On successful login: resets the failed-attempt counter and any active
 * lockout, clears this IP's throttle count, and responds with the account
 * summary. The session cookie itself is already set by Spring Security
 * before this handler runs.
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginSuccessHandler.class);

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final IpLoginThrottle ipLoginThrottle;
    private final ClientIpResolver clientIpResolver;

    public LoginSuccessHandler(
            UserRepository userRepository,
            ObjectMapper objectMapper,
            IpLoginThrottle ipLoginThrottle,
            ClientIpResolver clientIpResolver
    ) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.ipLoginThrottle = ipLoginThrottle;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        String username = authentication.getName();

        User user = userRepository.findByUsernameIgnoreCase(username).orElseThrow();
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        // Clear the streak marker too, so a stale timestamp can't make the next
        // failure look like a continuation of a streak that has been resolved.
        user.setLastFailedLoginAt(null);
        userRepository.save(user);

        ipLoginThrottle.recordSuccess(clientIpResolver.resolve(request));

        log.info("Login succeeded username={}", username);

        Role role = user.getRole();
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(LoginResponse.of(username, role)));
    }
}
