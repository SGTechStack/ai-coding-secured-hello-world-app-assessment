package com.example.auth.web;

import java.util.Map;

import com.example.auth.audit.AuditService;
import com.example.auth.user.RegistrationRequest;
import com.example.auth.user.RegistrationService;
import com.example.auth.user.UserPrincipal;
import com.example.auth.user.UserRepository;
import com.example.auth.user.UserResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationManager authenticationManager;
    private final HttpSessionSecurityContextRepository securityContextRepository;
    private final UserRepository users;
    private final AuditService audit;

    public AuthController(RegistrationService registrationService,
                          AuthenticationManager authenticationManager,
                          HttpSessionSecurityContextRepository securityContextRepository,
                          UserRepository users,
                          AuditService audit) {
        this.registrationService = registrationService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.users = users;
        this.audit = audit;
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegistrationRequest req) {
        return registrationService.register(req);
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(req.username(), req.password()));

            /*
             * Session fixation protection: change the session ID of any pre-existing
             * session (e.g. the anonymous session that held the CSRF cookie) before
             * binding the authenticated SecurityContext. If no session exists yet,
             * saveContext() below will create one with a fresh random ID.
             * DaoAuthenticationProvider.hideUserNotFoundExceptions=true (the default)
             * ensures a dummy BCrypt comparison runs even for unknown usernames,
             * preventing enumeration via response timing (Story 50).
             */
            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                request.changeSessionId();
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            audit.loginSuccess(principal.getUsername(), request.getRemoteAddr());
            return ResponseEntity.ok(new AuthResponse(principal.getUsername(), principal.getRole().name()));

        } catch (AuthenticationException ex) {
            // Generic message regardless of cause: unknown user, wrong password, locked,
            // or disabled — enumeration resistance requires identical responses (Stories 9, 18).
            audit.loginFailure(req.username(), request.getRemoteAddr());
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        // Invalidating the Spring Session JDBC session deletes it from the DB.
        // Spring Session's SessionRepositoryFilter then expires the session cookie
        // in the response, so the browser cookie is cleared automatically.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        // Re-fetch from DB to return live data (enabled/role may have changed since login).
        return users.findByUsername(principal.getUsername())
                .map(UserResponse::from)
                .orElseThrow();
    }
}
