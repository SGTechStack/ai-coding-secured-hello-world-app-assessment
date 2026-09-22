# Standalone: Session-Based Login with CSRF Bootstrap

## 1. Introduction

This guide defines the standard for implementing secure, session-based authentication in applications that manage their own user credentials. It provides a production-hardened implementation covering secure login flows, SPA-optimized CSRF protection using **Session-Based Storage**, and defense-in-depth against brute-force attacks.

**Key Design Principles:**
- **Zero-Trust Session Management**: Enforces absolute timeouts and concurrent session limits.
- **Defense-in-Depth**: Combines per-account rate limiting with automated database-backed lockout.
- **Session-Based CSRF**: Aligns with OWASP recommendations by storing CSRF tokens in the server-side session rather than cookies.
- **SPA Compatibility**: Resolves "Deferred Token" issues with a dedicated bootstrap endpoint to fetch the session-bound token.
- **Secure Error Handling**: Relies on Spring's default error mapping to return generic HTTP 401/403 responses without leaking system context.

**Note:** For browser-level controls (Headers, Cookies, CORS), see [Security Headers and SPA CSRF Configuration](../../Shared_Recipes/Common_Security_Headers_and_SPA_CSRF_Configuration.md).

## 2. Prerequisites

- Spring Boot 4.x with Spring Security 7.x
- **Spring Session**: Recommended for persistent session management in distributed environments.
- **Secure Hashing**: Credentials must be stored using **BCrypt** or **Argon2**.
- **Relational Database**: To persist user accounts and session state.

## 3. Implementation

### Step 1: Security Properties & Defaults

#### Security Defaults
Spring Security 7 provides the following protections automatically:
- **Session-Based CSRF**: Uses `HttpSessionCsrfTokenRepository` implicitly.
- **BREACH Protection**: Applies XOR encoding to CSRF tokens (`XorCsrfTokenRequestAttributeHandler`).
- **Session Fixation**: Rotates the session ID upon authentication.
- **Secure Logout**: Invalidates the session and clears the security context.

#### Configuration (application.yml)
```yaml
server:
  servlet:
    session:
      timeout: 15m
      cookie: { http-only: true, secure: true, same-site: lax }

app:
  security:
    auth:
      login-path: ${api.base-path}/login
      max-sessions: 1
      absolute-timeout-minutes: 480
      lockout-threshold: 5
      lockout-duration-minutes: 20
    rate-limit:
      login-attempts-per-minute: 10
```

### Step 2: Security Filter Chain

Configure the filter chain for SPA compatibility and session hardening, relying on Spring's defaults for CSRF and exception handling.

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // 1. Rely on default Session-based CSRF and XOR encoding
        .csrf(Customizer.withDefaults())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("${api.base-path}/csrf", "${api.base-path}/login").permitAll()
            .anyRequest().authenticated())
        .formLogin(form -> form
            .loginProcessingUrl("${api.base-path}/login")
            .successHandler(successHandler())
            .failureHandler(failureHandler()))
        // Logout keeps CSRF protection. On an expired session the token is gone, so
        // the CsrfFilter returns 403 before the LogoutFilter runs — expected. The SPA
        // handles the 401/403 (clears state, redirects to login), so no error page shows.
        .logout(logout -> logout
            .logoutUrl("${api.base-path}/logout")
            .deleteCookies("JSESSIONID", "SESSION"))
        .sessionManagement(session -> session
            .maximumSessions(1)
            .sessionRegistry(sessionRegistry()));

    return http.build();
}
```

### Step 3: CSRF Bootstrap Endpoint

Provides SPAs with the initial session-bound CSRF token required for the login request. The `/csrf` endpoint is configured as a common endpoint across both standalone and SSO modes. See [Security Headers and SPA CSRF Configuration](../../Shared_Recipes/Common_Security_Headers_and_SPA_CSRF_Configuration.md) for the implementation of the `/csrf` bootstrap controller.

### Step 4: Rate Limiting & Account Lockout

Layered defense against automated brute-force and credential stuffing.

```java
// 1. Rate Limiter (Per-Account Defense)
@Component
public class LoginRateLimiter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    public boolean isAllowed(String username) {
        Bucket bucket = buckets.computeIfAbsent(username, k -> Bucket.builder()
            .addLimit(Limit.of(10, Refill.intervally(10, Duration.ofMinutes(1))))
            .build());
        return bucket.tryConsume(1);
    }
}

// 2. Lockout Service (Database-Backed cooldown)
@Service
@RequiredArgsConstructor
public class AccountLockoutService {
    private final UserRepository users;

    @Transactional
    public void recordFailure(String username) {
        users.findByUsername(username).ifPresent(user -> {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= 5) {
                user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(20)));
            }
            users.save(user);
        });
    }
}
```

### Step 5: Secure Handlers & Hardened Timeout

Handlers must update metadata and return generic HTTP errors, allowing Spring Boot's default error mechanism to safely mask details.

```java
// Success: Reset security counters and return 200 OK
public void onAuthenticationSuccess(...) {
    String username = auth.getName();
    userAccountService.resetSecurityCounters(username);
    
    response.setStatus(HttpServletResponse.SC_OK);
}

// Failure: Increment counters and return 401 Unauthorized
public void onAuthenticationFailure(...) {
    String username = request.getParameter("username");
    if (username != null) lockoutService.recordFailure(username);
    
    // Spring Boot's BasicErrorController will format the JSON automatically
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials");
}

// Absolute Timeout Filter: hard limit on session lifetime
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    @Override
    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Instant start = (Instant) session.getAttribute("START_TIME");
            if (start == null) {
                session.setAttribute("START_TIME", Instant.now());
            } else if (Duration.between(start, Instant.now()).toMinutes() > 480) {
                session.invalidate();
                
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
```

## 4. Verification

- **CSRF Bootstrap**: `GET ${api.base-path}/csrf` returns a JSON object containing the token. Verify **no** `XSRF-TOKEN` cookie is set (confirming session-only storage).
- **Login Rotation**: Successful login returns `200 OK` and a new `JSESSIONID`.
- **Lockout Enforcement**: 5 failed attempts trigger a 401 response and a 20min lockout in the database.
- **Brute-Force Shield**: High-frequency attempts return `429 Too Many Requests`.
- **Absolute Timeout**: Sessions are invalidated after 8 hours regardless of activity.
- **Error Format**: Verify that unauthorized responses are generic (e.g., via Spring Boot's default `/error` mechanism) and do not leak system specifics.

## 5. Conclusion

This implementation provides a robust, defense-in-depth authentication model aligned with OWASP recommendations. By utilizing session-based CSRF storage, the application minimizes the exposure of security tokens while maintaining full compatibility with modern SPA architectures via the bootstrap endpoint. Relying on Spring Boot's default error controller provides a fail-secure posture with minimal boilerplate.

## 6. References

- [OWASP CSRF Prevention Cheat Sheet - Session-Based Storage](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#token-based-mitigation)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [NIST SP 800-63B: Digital Identity Guidelines](https://pages.nist.gov/800-63-3/)
