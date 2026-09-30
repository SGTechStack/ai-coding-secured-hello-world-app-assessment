package com.assessment.securedhelloworld.web;

import com.assessment.securedhelloworld.security.AbsoluteSessionTimeoutFilter;
import com.assessment.securedhelloworld.security.AppUserDetails;
import com.assessment.securedhelloworld.service.AuditLogService;
import com.assessment.securedhelloworld.web.dto.LoginRequest;
import com.assessment.securedhelloworld.web.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Login is handled manually (not Spring Security's form-login filter) so the API can speak pure
 * JSON. Every failure path below — wrong password, unknown username, locked account, disabled
 * account — collapses to the SAME generic 401 body, which is what makes enumeration resistance
 * (PRD Story 2) and "locked account still rejected" (PRD Story 3) hold uniformly rather than by
 * accident of exception type.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final ResponseEntity<Map<String, Object>> GENERIC_AUTH_FAILURE =
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "INVALID_CREDENTIALS", "message", "Invalid username or password"));

    private final org.springframework.security.authentication.AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final AuditLogService auditLogService;

    public AuthController(org.springframework.security.authentication.AuthenticationManager authenticationManager,
                           SessionAuthenticationStrategy sessionAuthenticationStrategy,
                           SecurityContextRepository securityContextRepository,
                           AuditLogService auditLogService) {
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        Authentication authRequest = UsernamePasswordAuthenticationToken.unauthenticated(loginRequest.username(), loginRequest.password());

        Authentication authResult;
        try {
            authResult = authenticationManager.authenticate(authRequest);
        } catch (AuthenticationException ex) {
            auditLogService.event("login", "failure", loginRequest.username(), null);
            return GENERIC_AUTH_FAILURE;
        }

        // Session-fixation protection: issues a fresh session id at the moment authentication
        // succeeds (IM8 as-11), via the same strategy bean SecurityConfig declares.
        sessionAuthenticationStrategy.onAuthentication(authResult, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        request.getSession().setAttribute(AbsoluteSessionTimeoutFilter.CREATED_AT_ATTRIBUTE, java.time.Instant.now());

        AppUserDetails principal = (AppUserDetails) authResult.getPrincipal();
        auditLogService.event("login", "success", principal.getUsername(), principal.getUsername());

        return ResponseEntity.ok(new LoginResponse(
                principal.getUsername(),
                principal.getUser().getRole().name(),
                principal.getUser().isForcePasswordChange()));
    }
}
