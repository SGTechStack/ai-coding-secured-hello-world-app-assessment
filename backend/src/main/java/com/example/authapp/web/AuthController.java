package com.example.authapp.web;

import com.example.authapp.domain.Role;
import com.example.authapp.domain.User;
import com.example.authapp.service.ApiException;
import com.example.authapp.service.LoginService;
import com.example.authapp.service.PasswordResetService;
import com.example.authapp.service.RegistrationService;
import com.example.authapp.web.Dtos.LoginRequest;
import com.example.authapp.web.Dtos.MeResponse;
import com.example.authapp.web.Dtos.MessageResponse;
import com.example.authapp.web.Dtos.RegisterRequest;
import com.example.authapp.web.Dtos.ResetConfirmRequest;
import com.example.authapp.web.Dtos.ResetRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private static final String GENERIC_LOGIN_ERROR = "Invalid username or password";
    private static final String GENERIC_RESET_MESSAGE =
            "If that email is registered, a password reset link has been sent.";

    private final RegistrationService registration;
    private final LoginService login;
    private final PasswordResetService passwordReset;
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfRepository;

    public AuthController(RegistrationService registration, LoginService login, PasswordResetService passwordReset,
            SecurityContextRepository contextRepository, CsrfTokenRepository csrfRepository) {
        this.registration = registration;
        this.login = login;
        this.passwordReset = passwordReset;
        this.contextRepository = contextRepository;
        this.csrfRepository = csrfRepository;
    }

    @GetMapping("/api/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<MeResponse> register(@Valid @RequestBody RegisterRequest body) {
        User user = registration.register(body.username(), body.email(), body.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(MeResponse.of(user));
    }

    @PostMapping("/api/auth/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        User user = login.authenticate(body.username(), body.password(), request.getRemoteAddr())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, GENERIC_LOGIN_ERROR));

        // Session-fixation protection: never keep a pre-login session id.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
                user.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        // Rotate the CSRF token on authentication change.
        csrfRepository.saveToken(csrfRepository.generateToken(request), request, response);
        return MeResponse.of(user);
    }

    @GetMapping("/api/auth/me")
    public MeResponse me(Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().substring("ROLE_".length());
        return new MeResponse(auth.getName(), Role.valueOf(role));
    }

    @PostMapping("/api/auth/password-reset/request")
    public MessageResponse requestReset(@Valid @RequestBody ResetRequest body) {
        passwordReset.requestReset(body.email());
        return new MessageResponse(GENERIC_RESET_MESSAGE);
    }

    @PostMapping("/api/auth/password-reset/confirm")
    public MessageResponse confirmReset(@Valid @RequestBody ResetConfirmRequest body) {
        passwordReset.confirmReset(body.token(), body.newPassword());
        return new MessageResponse("Password updated. Please log in.");
    }
}
