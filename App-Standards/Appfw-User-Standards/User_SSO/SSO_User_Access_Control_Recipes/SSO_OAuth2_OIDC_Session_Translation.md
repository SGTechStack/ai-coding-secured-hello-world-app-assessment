# OAuth2 OIDC SSO Session Translation

## 1. Introduction

This guide shows how to implement the core SSO login flow: browser redirect to an external OIDC provider, OIDC token validation, authorization-code callback handling with rate limiting, and translation of a successful external login into an internal server-side application session with security headers and session-based CSRF controls.

This matters because the SSO starter does not use the external bearer token as the application's primary session. By the end, the application will authenticate through an external provider with full OIDC compliance, while enforcing local session, **Session-Based CSRF**, concurrent-session, cookie security, security headers, and rate-limiting controls for its own APIs.

## 2. Prerequisites

- Spring Boot 4.x with Spring Security 7.x
- **Common Security Standards**: See [Security Headers and SPA CSRF Configuration](../../Shared_Recipes/Common_Security_Headers_and_SPA_CSRF_Configuration.md) for foundational browser defense.
- `spring-boot-starter-oauth2-client`
- An OIDC-compatible identity provider
- A persistent session store if concurrent-session invalidation must work across instances
- Lombok (for @Slf4j logging)

## 3. Steps

### 1. Configure the OAuth2 client and app session

Define the provider metadata, client registration, local session controls, and security settings.

```yaml
# File: src/main/resources/application.yml
server:
  servlet:
    session:
      timeout: 15m
      cookie:
        http-only: true
        secure: true
        same-site: lax

spring:
  security:
    oauth2:
      client:
        provider:
          keycloak:
            issuer-uri: https://idp.example.com/realms/app
            user-name-attribute: preferred_username
        registration:
          keycloak:
            client-id: user-login
            client-secret: ${OAUTH2_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: https://app.example.com/login/oauth2/code/keycloak
            scope: openid,profile,email  # 'openid' scope enables native OIDC nonce validation
            client-authentication-method: client_secret_basic
            # RFC 9700 (OAuth 2.0 Security BCP) recommends PKCE for all clients, including confidential ones.
            # Spring Security does not enable PKCE automatically for client_secret_basic.
            # Enable it on the IdP registration (e.g. Keycloak → "Proof Key for Code Exchange" → S256),
            # and Spring Security will include code_challenge/code_verifier automatically.

app:
  security:
    auth:
      max-concurrent-sessions: 1
      invalid-session-url: /login?expired
      absolute-timeout-minutes: 480
    sso:
      rate-limit:
        callback-attempts-per-minute: 10  # Per client IP
    headers:
      enabled: true
    cors:
      allowed-origins:
        - https://app.example.com
```

### 2. Configure OAuth2 login with local session behavior

Use OAuth2 login for authentication. Spring Security natively handles OIDC token validation and nonce replay protection when the `openid` scope is present.

```java
// File: src/main/java/com/example/security/SsoSecurityConfig.java
@Configuration
@EnableWebSecurity
public class SsoSecurityConfig {

    @Bean
    SecurityFilterChain appSecurity(
            HttpSecurity http,
            SessionRegistry sessionRegistry,
            GrantedAuthoritiesMapper authoritiesMapper,
            AuthenticationSuccessHandler successHandler,
            AuthenticationFailureHandler failureHandler,
            SsoLogoutAuditHandler ssoLogoutAuditHandler,
            CallbackRateLimitFilter callbackRateLimitFilter,
            LogoutSuccessHandler logoutSuccessHandler) throws Exception {

        http
            // Common security (Headers, CORS, CSRF) applied here via defaults
            .csrf(Customizer.withDefaults())
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(endpoint -> endpoint.userAuthoritiesMapper(authoritiesMapper))
                .successHandler(successHandler)
                .failureHandler(failureHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("${api.base-path}/csrf", "/oauth2/**", "/login/**").permitAll()
                .anyRequest().authenticated())
            .sessionManagement(session -> session
                .invalidSessionUrl("/login?expired")
                .sessionFixation(fixation -> fixation.changeSessionId())  // Spring Security 7 default; rotates ID in-place without losing session attributes
                .maximumSessions(1)
                .sessionRegistry(sessionRegistry))
            // Logout keeps CSRF protection. On an expired session the token is gone, so
            // the CsrfFilter returns 403 before the LogoutFilter runs — expected. The SPA
            // handles the 401/403 (clears state, redirects to login), so no error page shows.
            .logout(logout -> logout
                .addLogoutHandler(ssoLogoutAuditHandler)  // Runs before SecurityContextLogoutHandler; session still live
                .deleteCookies("JSESSIONID", "SESSION")
                .invalidateHttpSession(true)
                .logoutSuccessHandler(logoutSuccessHandler));

        // Rate-limit the callback before OAuth2LoginAuthenticationFilter makes outbound token exchange calls
        http.addFilterBefore(callbackRateLimitFilter, OAuth2LoginAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return authorities -> authorities;  // Or map OIDC claims to local roles
    }

    // Defined as @Bean (not @Component) to prevent Spring Boot from auto-registering it
    // as a servlet filter — it is registered explicitly in the security chain above.
    @Bean
    CallbackRateLimitFilter callbackRateLimitFilter() {
        return new CallbackRateLimitFilter();
    }

    // SessionRegistryImpl is sufficient for single-instance deployments.
    // For distributed deployments, replace with SpringSessionBackedSessionRegistry
    // (backed by FindByIndexNameSessionRepository) so the registry is shared across
    // instances — and the ConcurrentSessionLimitFilter below becomes redundant for
    // enforcement (though it still provides the audit trail).
    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }
}
```

```java
// File: src/main/java/com/example/security/CallbackRateLimitFilter.java
// Limits callback attempts per client IP to prevent flooding the IdP's token endpoint.
// An attacker can initiate the SSO flow repeatedly to obtain valid state parameters,
// then submit invalid codes — each attempt forces an outbound token exchange call to the IdP.
// Not annotated @Component — registered explicitly via http.addFilterBefore() to prevent
// Spring Boot from also auto-registering it as a servlet filter (which would cause double execution).
@Slf4j
public class CallbackRateLimitFilter extends OncePerRequestFilter {

    private static final String CALLBACK_PATH_PREFIX = "/login/oauth2/code/";
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.security.sso.rate-limit.callback-attempts-per-minute:10}")
    private int attemptsPerMinute;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(CALLBACK_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(clientIp, k -> Bucket.builder()
            .addLimit(Limit.of(attemptsPerMinute, Refill.intervally(attemptsPerMinute, Duration.ofMinutes(1))))
            .build());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.atWarn()
               .setMessage("Callback rate limit exceeded")
               .addKeyValue("client.ip", clientIp)
               .addKeyValue("event.action", "user-authentication")
               .addKeyValue("event.outcome", "failure")
               .addKeyValue("reason", "rate-limit-exceeded")
               .log();
            response.sendError(HttpServletResponse.SC_TOO_MANY_REQUESTS, "Too many requests");
        }
    }
}
```

> **Note:** `request.getRemoteAddr()` returns the load balancer IP when deployed behind a reverse proxy. In that case, extract the real client IP from `X-Forwarded-For`, validated against trusted proxy ranges.
>
> **Note:** The `ConcurrentHashMap<String, Bucket>` grows unbounded under a distributed attack with many source IPs. For production deployments without an upstream WAF or API gateway handling rate limiting, replace with a TTL-evicting cache (e.g. Caffeine `Cache.expireAfterAccess`).

### 3. Record local session context with audit logging

Persist local session metadata and audit the SSO login.

```java
// File: src/main/java/com/example/security/SsoLoginSuccessHandler.java
@Slf4j
@Component
public class SsoLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final SsoAuditService auditService;


    SsoLoginSuccessHandler(SsoAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();
        HttpSession session = request.getSession(false);

        if (session != null) {
            // With Spring Session, getId() returns the internal Spring Session UUID —
            // not the SESSION cookie value the browser holds. The cookie is a separate
            // token that maps to this ID in the session store. Hashing the internal ID
            // is safe and sufficient as an audit correlation key across login, logout,
            // and timeout events; it must never appear in logs unhashed.
            String sessionId = session.getId();
            String hashedSessionId = DigestUtils.sha256Hex(sessionId);
            
            session.setAttribute("SESSION_HASH", hashedSessionId);
            session.setAttribute("USERNAME", username);

            auditService.logSsoLoginSuccess(hashedSessionId);
            log.atInfo()
               .setMessage("SSO login succeeded")
               .addKeyValue("session.hash", hashedSessionId)
               .addKeyValue("event.action", "user-authentication")
               .addKeyValue("event.outcome", "success")
               .log();
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}

// File: src/main/java/com/example/security/SsoLoginFailureHandler.java
@Slf4j
@Component
public class SsoLoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final SsoAuditService auditService;


    SsoLoginFailureHandler(SsoAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String registrationId = extractRegistrationId(request.getRequestURI());

        // Invalidate any existing local session
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
            log.atWarn()
               .setMessage("SSO login failed and existing session was invalidated")
               .addKeyValue("registration.id", registrationId)
               .addKeyValue("reason", "authentication_failed")
               .addKeyValue("event.action", "user-authentication")
               .addKeyValue("event.outcome", "failure")
               .log();
        }

        auditService.logSsoLoginFailure(registrationId, "authentication_failed");
        super.onAuthenticationFailure(request, response, exception);
    }

    private String extractRegistrationId(String path) {
        String[] parts = path.split("/");
        return parts.length > 4 ? parts[4] : "unknown";
    }
}
```

```java
// File: src/main/java/com/example/security/SsoLogoutAuditHandler.java
@Slf4j
@Component
public class SsoLogoutAuditHandler implements LogoutHandler {

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        HttpSession session = request.getSession(false);
        // Session is still live here — LogoutHandler runs before SecurityContextLogoutHandler invalidates it
        String sessionHash = session != null ? (String) session.getAttribute("SESSION_HASH") : null;

        if (sessionHash != null) {
            log.atInfo()
               .setMessage("User logged out")
               .addKeyValue("session.hash", sessionHash)
               .addKeyValue("event.action", "user-session")
               .addKeyValue("event.outcome", "success")
               .addKeyValue("reason", "logout")
               .log();
        }
    }
}
```

### 4. Expose the CSRF bootstrap endpoint

Once external login succeeds, browser clients still need a local CSRF token for state-changing application requests. See [Security Headers and SPA CSRF Configuration](../../Shared_Recipes/Common_Security_Headers_and_SPA_CSRF_Configuration.md) for the implementation of the `/csrf` endpoint.

### 5. Configure absolute session timeout

Enforce maximum session lifetime. Unlike idle timeout (routine housekeeping), absolute timeout is a hard policy enforcement worth auditing — it indicates a long-running session was forcibly cut off.

```java
// File: src/main/java/com/example/security/AbsoluteSessionTimeoutFilter.java
@Slf4j
@Component
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    @Value("${app.security.auth.absolute-timeout-minutes:480}")
    private int absoluteTimeoutMinutes;

    private static final String LAST_SESSION_START = "LAST_SESSION_START";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);

        if (session != null) {
            Long sessionStartTime = (Long) session.getAttribute(LAST_SESSION_START);

            if (sessionStartTime == null) {
                session.setAttribute(LAST_SESSION_START, System.currentTimeMillis());
            } else {
                long elapsedMinutes = (System.currentTimeMillis() - sessionStartTime) / (1000 * 60);
                if (elapsedMinutes > absoluteTimeoutMinutes) {
                    // Read before invalidate — attributes are inaccessible after session.invalidate()
                    String sessionHash = (String) session.getAttribute("SESSION_HASH");
                    session.invalidate();

                    log.atInfo()
                       .setMessage("Session expired due to absolute timeout")
                       .addKeyValue("session.hash", sessionHash)
                       .addKeyValue("event.action", "user-session")
                       .addKeyValue("event.outcome", "success")
                       .addKeyValue("reason", "absolute-timeout")
                       .log();

                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

### Step 6: Enforce Concurrent Session Limits (Distributed Deployment)

Limit the number of concurrent sessions per user to prevent session abuse and enforce single-login policies.

```java
// File: src/main/java/com/example/security/ConcurrentSessionLimitFilter.java
@Component
public class ConcurrentSessionLimitFilter extends OncePerRequestFilter {

    private final RedisIndexedSessionRepository sessionRepository;
    private final AuditLogger auditLogger;

    @Value("${app.security.auth.max-concurrent-sessions:1}")
    private int maxConcurrentSessions;

    public ConcurrentSessionLimitFilter(RedisIndexedSessionRepository sessionRepository,
                                      AuditLogger auditLogger) {
        this.sessionRepository = sessionRepository;
        this.auditLogger = auditLogger;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);

        if (session != null && request.getUserPrincipal() != null) {
            String principalName = request.getUserPrincipal().getName();

            // Find all active sessions for this user
            var userSessions = sessionRepository.findByIndexNameAndIndexValue(
                RedisIndexedSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);

            if (userSessions.size() > maxConcurrentSessions) {
                // Invalidate all but the current session (oldest sessions first)
                userSessions.values().stream()
                    .filter(s -> !s.getId().equals(session.getId()))
                    .sorted(Comparator.comparingLong(s -> s.getLastAccessedTime().toEpochMilli()))
                    .limit(userSessions.size() - maxConcurrentSessions)
                    .forEach(s -> {
                        sessionRepository.deleteById(s.getId());
                        auditLogger.logSessionInvalidatedForConcurrencyLimit(
                            principalName, s.getId(), maxConcurrentSessions);
                    });
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

## 4. Examples

### Start the SSO login flow

The user begins authentication by hitting the registration-specific authorization endpoint. Nonce generation and OIDC validation are handled automatically by the framework.

```bash
# Browser request (automatic via form or link)
GET /oauth2/authorization/keycloak

# Redirects to IdP with native OIDC parameters:
# https://idp.example.com/realms/app/protocol/openid-connect/auth?
#   client_id=user-login&
#   redirect_uri=https://app.example.com/login/oauth2/code/keycloak&
#   response_type=code&
#   scope=openid+profile+email&
#   nonce=<auto-generated-nonce>
```

### Bootstrap CSRF after successful login

After the browser is redirected back and the local session exists, fetch the CSRF token before mutating application state.

```bash
curl -i -b cookies.txt https://app.example.com${api.base-path}/csrf
```

**Audit log output:**
```json
{"@timestamp":"2026-03-02T10:15:42.456Z","log.level":"INFO","message":"SSO login succeeded","session.hash":"5d41402abc4b2a76b9719d911017c592...","event.action":"user-authentication","event.outcome":"success"}
```

## 5. Verification

Confirm that:

- `/oauth2/authorization/{registrationId}` redirects to the IdP with an auto-generated nonce;
- OIDC token validation (signatures, issuer, audience, nonce) is handled natively by the framework;
- a successful callback creates a local session with hashed session ID;
- failed authentication invalidates any existing local session;
- callback rate limiting returns 429 on breach;
- `${api.base-path}/csrf` returns a token after login; Verify **no** `XSRF-TOKEN` cookie is set.
- security headers are present in all responses;
- absolute session timeout (8 hours) expires long-running sessions and emits an audit log with `reason: absolute-timeout`;
- logout invalidates the local session, clears cookies, and emits an audit log with `reason: logout`;
- all authentication and session lifecycle events are audited with session hash and outcome.

## 6. Conclusion

This implementation provides a clean, OIDC-compliant SSO entry point by leveraging Spring Security's native features for authentication and nonce protection, while applying custom controls for local session security, rate limiting, and auditing.

## 7. References

- [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html)
- [Spring Security Session Management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
