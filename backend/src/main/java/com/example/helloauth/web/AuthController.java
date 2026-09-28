package com.example.helloauth.web;

import com.example.helloauth.domain.User;
import com.example.helloauth.security.AppUserDetails;
import com.example.helloauth.security.AuditLogger;
import com.example.helloauth.service.AuthService;
import com.example.helloauth.service.PasswordPolicy;
import com.example.helloauth.service.ServiceExceptions;
import com.example.helloauth.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AuthController {

    private static final String USER_ID_ATTR = "USER_ID";

    private final AuthService authService;
    private final UserService userService;
    private final SessionRegistry sessionRegistry;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditLogger audit;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(AuthService authService, UserService userService,
                          SessionRegistry sessionRegistry, PasswordEncoder passwordEncoder,
                          PasswordPolicy passwordPolicy, AuditLogger audit,
                          SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.userService = userService;
        this.sessionRegistry = sessionRegistry;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.securityContextRepository = securityContextRepository;
    }

    /** Lets the SPA obtain a CSRF cookie before submitting state-changing requests. */
    @GetMapping("/csrf")
    public ResponseEntity<Dtos.MessageResponse> csrf() {
        return ResponseEntity.ok(new Dtos.MessageResponse("ok"));
    }

    @PostMapping("/register")
    public ResponseEntity<Dtos.MessageResponse> register(@Valid @RequestBody Dtos.RegisterRequest req) {
        userService.register(req.username(), req.email(), req.password());
        return ResponseEntity.status(201).body(new Dtos.MessageResponse("Account created."));
    }

    @PostMapping("/login")
    public ResponseEntity<Dtos.LoginResponse> login(@Valid @RequestBody Dtos.LoginRequest req,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        String ip = clientIp(request);
        User user = authService.authenticate(req.username(), req.password(), ip);

        // Session-fixation protection: if a pre-authentication session already carries a security
        // context, drop it and start fresh. A brand-new (anonymous) session is reused as-is.
        HttpSession existing = request.getSession(false);
        if (existing != null && existing.getAttribute(
                org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) != null) {
            existing.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(USER_ID_ATTR, user.getId());

        AppUserDetails principal = new AppUserDetails(user);
        Authentication auth = new PreAuthenticatedAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        // Persist via the repository so the filter chain reads it back on subsequent requests.
        securityContextRepository.saveContext(context, request, response);

        // Register the session so it can be expired per-user (admin action / password reset).
        sessionRegistry.registerNewSession(session.getId(), principal);

        return ResponseEntity.ok(new Dtos.LoginResponse(
                user.getUsername(), user.getRole().name(), user.isMustChangePassword()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Dtos.MessageResponse> logout(HttpServletRequest request,
                                                       HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String username = currentUsername();
            sessionRegistry.removeSessionInformation(session.getId());
            session.invalidate();
            if (username != null) {
                audit.logout(username);
            }
        }
        SecurityContextHolder.clearContext();
        // Clear the session cookie on the client.
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("JSESSIONID", "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(new Dtos.MessageResponse("Logged out."));
    }

    /** Forced/self-service password change (IM8 ac-6 first-login change path). */
    @PostMapping("/account/change-password")
    public ResponseEntity<Dtos.MessageResponse> changePassword(
            @Valid @RequestBody Dtos.ChangePasswordRequest req) {
        String username = currentUsername();
        if (username == null) {
            return ResponseEntity.status(401).body(new Dtos.MessageResponse("Unauthenticated."));
        }
        User user = userService.requireByUsername(username);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ServiceExceptions.ValidationException("Current password is incorrect.");
        }
        if (!passwordPolicy.isValid(req.newPassword())) {
            throw new ServiceExceptions.ValidationException(passwordPolicy.requirementMessage());
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        boolean wasForced = user.isMustChangePassword();
        user.setMustChangePassword(false);
        userService.save(user);
        if (wasForced) {
            audit.forcedPasswordChange(username);
        }
        return ResponseEntity.ok(new Dtos.MessageResponse("Password changed."));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AppUserDetails details) {
            return details.getUsername();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
