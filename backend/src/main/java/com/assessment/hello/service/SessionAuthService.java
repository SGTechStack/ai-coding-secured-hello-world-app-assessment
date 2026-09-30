package com.assessment.hello.service;

import com.assessment.hello.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Bridges our custom credential check into Spring Security's session-backed
 * SecurityContext. On login we create a fresh session (session-fixation defense),
 * store an authenticated context, and let Spring Session persist it in the cookie.
 */
@Service
public class SessionAuthService {

    public static final String LOGIN_AT_ATTR = "LOGIN_AT";

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public void establishSession(User user, HttpServletRequest request, HttpServletResponse response) {
        // Invalidate any existing session and start a new one to prevent fixation.
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        HttpSession session = request.getSession(true);
        // Record when this session was authenticated so it can be compared against the
        // user's sessionsValidFrom epoch (bumped on password reset).
        session.setAttribute(LOGIN_AT_ATTR, Instant.now());

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    public void endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
