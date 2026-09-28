package com.example.helloauth.web;

import com.example.helloauth.domain.Role;
import com.example.helloauth.service.AuthenticationService;
import com.example.helloauth.service.AuthenticationService.AuthenticatedAccount;
import com.example.helloauth.service.PasswordResetService;
import com.example.helloauth.service.RegistrationService;
import com.example.helloauth.service.SessionService;
import com.example.helloauth.web.dto.ApiPayloads.CsrfTokenResponse;
import com.example.helloauth.web.dto.ApiPayloads.LoginRequest;
import com.example.helloauth.web.dto.ApiPayloads.MessageResponse;
import com.example.helloauth.web.dto.ApiPayloads.PasswordResetConfirmRequest;
import com.example.helloauth.web.dto.ApiPayloads.PasswordResetRequest;
import com.example.helloauth.web.dto.ApiPayloads.RegisterRequest;
import com.example.helloauth.web.dto.ApiPayloads.SessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, login, session introspection and password reset.
 *
 * <p>Logout is conspicuously absent: it is handled by Spring Security's logout filter, configured in
 * {@code SecurityConfig}. A controller method would have to invalidate the session, clear the
 * security context and expire the cookie by hand, which is precisely what the filter already does
 * correctly.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;
    private final SessionService sessionService;

    public AuthController(
            RegistrationService registrationService,
            AuthenticationService authenticationService,
            PasswordResetService passwordResetService,
            SessionService sessionService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.passwordResetService = passwordResetService;
        this.sessionService = sessionService;
    }

    /**
     * Hands the CSRF token to the frontend.
     *
     * <p>A cross-origin client cannot read the token from a cookie — {@code document.cookie} on the
     * frontend's origin cannot see a cookie set by the backend's, whatever the {@code HttpOnly} flag
     * says — so the usual double-submit shortcut is unavailable and the token is served here instead.
     * Handing out a CSRF token is safe: the protection rests on an attacker's page being unable to
     * <em>read</em> a cross-origin response, not on the token being secret from the legitimate
     * client.
     */
    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken token) {
        return new CsrfTokenResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        registrationService.register(request.username(), request.email(), request.password());
        return new MessageResponse("Account created. You can now log in.");
    }

    @PostMapping("/login")
    public SessionResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthenticatedAccount account =
                authenticationService.login(
                        request.username(), request.password(), ClientAddress.of(httpRequest));
        sessionService.startSession(
                account.username(), account.role(), httpRequest, httpResponse);
        return SessionResponse.of(account.username(), account.role());
    }

    /**
     * @param authentication null for a visitor, because this endpoint is deliberately open
     */
    @GetMapping("/me")
    public SessionResponse me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return SessionResponse.anonymous();
        }
        return SessionResponse.of(authentication.getName(), roleOf(authentication));
    }

    @PostMapping("/password-reset/request")
    public MessageResponse requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request, HttpServletRequest httpRequest) {
        passwordResetService.requestReset(request.email(), ClientAddress.of(httpRequest));
        // Identical whether or not the address is registered. That is the requirement, not an
        // oversight, so there is nothing to branch on here.
        return new MessageResponse(
                "If that email is registered, a password reset link is on its way.");
    }

    @PostMapping("/password-reset/confirm")
    public MessageResponse confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.password());
        return new MessageResponse("Your password has been changed. You can now log in.");
    }

    private static Role roleOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .map(Role::valueOf)
                .findFirst()
                .orElse(Role.USER);
    }
}
