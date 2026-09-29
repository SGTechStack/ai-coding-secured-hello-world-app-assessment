package com.example.securedhello.controller;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.controller.dto.LoginRequest;
import com.example.securedhello.entity.User;
import com.example.securedhello.service.AuthenticationService;

/**
 * Authentication endpoints. {@code GET /api/csrf} lets the cross-origin SPA
 * obtain a CSRF token (and the XSRF cookie) before authenticating.
 * {@code POST /api/login} verifies credentials via {@link
 * AuthenticationService} and, on success, establishes a server-side Session by
 * persisting the {@link SecurityContext} through the {@link
 * SecurityContextRepository}.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(AuthenticationService authenticationService,
                          SecurityContextRepository securityContextRepository) {
        this.authenticationService = authenticationService;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(@RequestAttribute(name = "_csrf") CsrfToken token) {
        return Map.of(
                "token", token.getToken(),
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        User user = authenticationService.authenticate(
                request.username(), request.password(), httpRequest.getRemoteAddr());

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        user.getUsername(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return Map.of(
                "username", user.getUsername(),
                "role", user.getRole().name());
    }
}
