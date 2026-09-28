package com.example.helloworldauth.auth;

import com.example.helloworldauth.config.InMemoryIndexedSessionRepository;
import com.example.helloworldauth.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.MapSession;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoginController {

    private final LoginService loginService;
    private final InMemoryIndexedSessionRepository indexedSessions;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public LoginController(LoginService loginService, InMemoryIndexedSessionRepository indexedSessions) {
        this.loginService = loginService;
        this.indexedSessions = indexedSessions;
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest,
                                     jakarta.servlet.http.HttpServletResponse httpResponse) {
        User user = loginService.authenticate(
            request.username(), request.password(), httpRequest.getRemoteAddr());

        // Establish an authenticated server-side session. session-fixation
        // protection (configured in SecurityConfig) rotates the id on login.
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            user.getUsername(), null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        String servletSessionId = httpRequest.getSession(true).getId();
        contextRepository.saveContext(context, httpRequest, httpResponse);

        // Mirror the authenticated session into the principal-name-indexed store,
        // keyed by the servlet session id. This is what lets the password-reset
        // confirm flow find and delete every session for a user (Story 7). The
        // mirror carries the SPRING_SECURITY_CONTEXT so the principal-name index
        // resolves to this user.
        MapSession indexed = new MapSession(servletSessionId);
        indexed.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        indexedSessions.save(indexed);

        return Map.of("username", user.getUsername(), "role", user.getRole().name());
    }
}
