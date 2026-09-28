package com.example.auth.auth;

import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login and logout are handled entirely by Spring Security's filter chain
 * (see {@code SecurityConfig}) -- a custom JSON login filter for login, and
 * the built-in logout support for logout. {@code GET /api/auth/me} is now
 * gated by {@code .authenticated()} in {@code SecurityConfig}, so an
 * anonymous request never reaches this controller; the local check here is
 * defense-in-depth for the (authenticated-but-user-row-vanished) edge case
 * rather than the primary 401/200 decision.
 */
@RestController
public class AuthController {

    private final UserRepository userRepository;
    private final RegistrationService registrationService;

    public AuthController(UserRepository userRepository, RegistrationService registrationService) {
        this.userRepository = userRepository;
        this.registrationService = registrationService;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(
                        user.getUsername(), user.getEmail(), user.getRole(), user.isEnabled(), user.getCreatedAt()));
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<?> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(401).build();
        }

        Optional<User> user = userRepository.findByUsername(authentication.getName());
        return user.<ResponseEntity<?>>map(
                        u -> ResponseEntity.ok(new MeResponse(u.getUsername(), u.getEmail(), u.getRole())))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
