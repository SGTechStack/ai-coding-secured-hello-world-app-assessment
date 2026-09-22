package com.sgtechstack.helloworldauthapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * On failed login: increments the failed-attempt counter for the targeted
 * account (if it exists) and always responds with the same generic
 * message, so a caller cannot distinguish "wrong password" from "no such
 * username" — the enumeration-resistance requirement.
 *
 * Actual lockout enforcement (locking the account after N failures) is
 * ticket 04's job; this only tracks the counter that ticket will read.
 */
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);
    private static final String GENERIC_MESSAGE = "Invalid username or password";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public LoginFailureHandler(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        String username = request.getParameter("username");

        if (username != null) {
            userRepository.findByUsernameIgnoreCase(username).ifPresent(user -> {
                user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
                userRepository.save(user);
            });
        }

        log.info("Login failed username={}", username);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(GENERIC_MESSAGE)));
    }
}
