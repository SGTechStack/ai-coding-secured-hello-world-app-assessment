package com.example.auth.web;

import java.util.Map;

import com.example.auth.login.IpThrottledException;
import com.example.auth.login.LoginService;
import com.example.auth.passwordreset.PasswordResetConfirmRequest;
import com.example.auth.passwordreset.PasswordResetRequestRequest;
import com.example.auth.passwordreset.PasswordResetService;
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
    private final LoginService loginService;
    private final PasswordResetService passwordResetService;
    private final HttpSessionSecurityContextRepository securityContextRepository;
    private final UserRepository users;

    public AuthController(RegistrationService registrationService,
                          LoginService loginService,
                          PasswordResetService passwordResetService,
                          HttpSessionSecurityContextRepository securityContextRepository,
                          UserRepository users) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.passwordResetService = passwordResetService;
        this.securityContextRepository = securityContextRepository;
        this.users = users;
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
            Authentication auth = loginService.authenticate(
                    req.username(), req.password(), request.getRemoteAddr());

            // Session fixation protection (see Slice 3 comment).
            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                request.changeSessionId();
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            return ResponseEntity.ok(new AuthResponse(principal.getUsername(), principal.getRole().name()));

        } catch (IpThrottledException ex) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);

        } catch (AuthenticationException ex) {
            // Generic 401 regardless of cause (wrong password, unknown user, locked,
            // disabled) — enumeration resistance requires identical responses.
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Invalid credentials");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return users.findByUsername(principal.getUsername())
                .map(UserResponse::from)
                .orElseThrow();
    }

    /**
     * Always returns the same generic 200 regardless of whether the email exists
     * — account existence must not be inferable (enumeration resistance, Story 18).
     * A ResetRequestThrottledException (429) is the only non-200 outcome and is
     * IP-keyed, so it leaks no account information.
     */
    @PostMapping("/password-reset/request")
    Map<String, String> requestPasswordReset(@Valid @RequestBody PasswordResetRequestRequest req,
                                              HttpServletRequest request) {
        passwordResetService.requestReset(req.email(), request.getRemoteAddr());
        return Map.of("message", "If that email is registered, a reset link has been sent.");
    }

    /**
     * Confirms a reset. Does not authenticate the user (Story 24). Invalid,
     * expired, or used tokens all yield a generic 400 via InvalidResetTokenException.
     */
    @PostMapping("/password-reset/confirm")
    Map<String, String> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest req) {
        passwordResetService.confirmReset(req.token(), req.newPassword());
        return Map.of("message", "Password has been reset. Please log in.");
    }
}
