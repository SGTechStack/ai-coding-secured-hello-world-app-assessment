package com.example.helloauth.auth;

import com.example.helloauth.user.User;
import com.example.helloauth.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The auth spine: register, controller-driven login, CSRF bootstrap, and the
 * {@code /me} session probe.
 *
 * <p>Login is the documented "Storing the Authentication manually" pattern —
 * there is no authentication filter on the Security 6+/7 line, so this
 * controller must itself (ticket 01 findings):
 * <ol>
 *   <li>invoke the {@link SessionAuthenticationStrategy} (session-id change
 *       for fixation protection + CSRF token rotation), and</li>
 *   <li>persist the context via {@link SecurityContextRepository#saveContext}
 *       — {@code SecurityContextHolderFilter} only loads it.</li>
 * </ol>
 * The {@code sessionManagement().sessionFixation(...)} DSL is a no-op without
 * an auth filter, which is why none of this is delegated to the chain.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final LoginService loginService;
    private final IpThrottleService ipThrottle;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
        SecurityContextHolder.getContextHolderStrategy();

    public AuthController(UserService userService, LoginService loginService,
            IpThrottleService ipThrottle,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.userService = userService;
        this.loginService = loginService;
        this.ipThrottle = ipThrottle;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    @PostMapping("/register")
    public ResponseEntity<PrincipalResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {
        // Anonymous-request throttle (security-review F-04): register is an
        // unauthenticated mutation — a user row plus a BCrypt hash per call.
        // Same ordering convention as the login path's defense-in-depth:
        // check → record → service, so a throttled hit moves nothing and
        // every allowed hit burns budget.
        String remoteAddr = servletRequest.getRemoteAddr();
        if (remoteAddr != null) {
            if (ipThrottle.isAnonThrottled(remoteAddr)) {
                throw new LoginThrottledException(
                    "Too many requests. Try again later.");
            }
            ipThrottle.recordAnonRequest(remoteAddr);
        }
        User user = userService.register(
            request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new PrincipalResponse(user.getUsername(), user.getRole().name()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        Authentication authentication;
        try {
            // LoginService runs the ordered two-layer defense: IP-throttle →
            // account-lock → credentials (ticket 11). A throttled source gets
            // LoginThrottledException — not an AuthenticationException — so it
            // falls through to the 429 handler in ApiExceptionHandler.
            authentication = loginService.login(
                request.username(), request.password(), servletRequest.getRemoteAddr());
        } catch (AuthenticationException ex) {
            // One generic message for every failure mode — bad credentials,
            // locked, disabled — so account existence can't be inferred
            // (enumeration resistance is a security AC, not a nicety).
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Invalid username or password.");
            problem.setTitle("Login failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }

        sessionAuthenticationStrategy.onAuthentication(
            authentication, servletRequest, servletResponse);

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        return ResponseEntity.ok(
            new PrincipalResponse(authentication.getName(), roleOf(authentication)));
    }

    /**
     * CSRF bootstrap. {@code csrf.spa()} defers token generation — the
     * {@code XSRF-TOKEN} cookie is only written once something touches the
     * token. Injecting it here forces that, which is why the SPA calls this
     * at startup and again after login/logout (both clear the token).
     */
    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    /**
     * Session probe for the SPA's page-load auth detection. Lives under the
     * permit-all {@code /api/auth/**} prefix, so the anonymous check happens
     * here — 401, matching the chain's entry-point semantics.
     */
    @GetMapping("/me")
    public ResponseEntity<PrincipalResponse> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(
            new PrincipalResponse(authentication.getName(), roleOf(authentication)));
    }

    /**
     * First {@code ROLE_*} authority, prefix stripped. (Security 7 tokens may
     * also carry a {@code FACTOR_PASSWORD} authority — filter it out.)
     */
    private static String roleOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith("ROLE_"))
            .map(authority -> authority.substring("ROLE_".length()))
            .findFirst()
            .orElse("USER");
    }
}
