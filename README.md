# Hello Auth — Web Security and Production Configuration

This guide introduces the security architecture and production configuration of
**hello-auth**: a React SPA and a Spring Boot 4.1 (Java 21) JSON API using
server-side, cookie-based sessions. In production, Spring Session stores sessions
in the same external PostgreSQL or MySQL database whose schema is managed by
Liquibase.

## Architecture at a glance

```text
Browser / React SPA
  credentials: include
  X-XSRF-TOKEN request header
          |
        HTTPS
          v
Trusted TLS-terminating proxy
  strips client-supplied forwarding headers
  sets trusted X-Forwarded-* values
          |
          v
Spring Boot JSON API
  Spring Security + Spring Session JDBC
          |
          v
PostgreSQL or MySQL
```

| Concern | Mechanism | Primary source |
|---|---|---|
| Authentication | Controller-driven login and BCrypt password hashes | `AuthController`, `SecurityConfig` |
| Session | Spring Session JDBC with server-side invalidation | `application.yml`, `SecurityConfig` |
| CSRF | `XSRF-TOKEN` cookie plus `X-XSRF-TOKEN` request header | `SecurityConfig.csrfTokenRepository()` |
| Authorization | Explicit public paths; `/api/admin/**` requires `ADMIN` | `SecurityConfig.securityFilterChain()` |
| Brute-force defense | Per-IP throttle plus per-account lockout | `IpThrottleService`, `LoginService` |
| Audit | Structured, commit-aware JSON security events | `AuditLogger`, `logback-spring.xml` |
| Production configuration | Environment-backed datasource, admin, CORS, and reset settings | `application-prod.yml` |

## Authentication and server-side sessions

The application does not enable Spring Security form login or HTTP Basic.
`AuthController` performs login and explicitly invokes the configured
`SessionAuthenticationStrategy`, then saves the `SecurityContext`. The strategy
rotates the CSRF token and changes the session ID to protect against session
fixation.

The following is an **abridged call-site excerpt**, not a standalone method:

```java
sessionAuthenticationStrategy.onAuthentication(
    authentication, servletRequest, servletResponse);
securityContextRepository.saveContext(
    context, servletRequest, servletResponse);
```

Spring Session JDBC stores each authenticated session as database rows. Logout
invalidates the server-side session and clears the `SESSION` cookie, so replaying
the old cookie does not restore authentication. The shared configuration sets a
30-minute idle timeout:

```yaml
# Exact excerpt from backend/src/main/resources/application.yml
spring:
  session:
    store-type: jdbc
    timeout: 30m
```

Passwords use `BCryptPasswordEncoder`. The password policy is length-only and is
controlled by `app.password-min-length` (12 by default).

## CSRF protection and cookies

The SPA CSRF pattern uses two values:

1. `SESSION` identifies the server-side session. Production makes this cookie
   `Secure`, `HttpOnly`, and `SameSite=Strict` through
   `server.servlet.session.cookie.*`.
2. `XSRF-TOKEN` carries the CSRF token. It is intentionally readable by
   JavaScript so the SPA can copy it into the `X-XSRF-TOKEN` header. Its
   `SameSite` value is set to `Strict` in `SecurityConfig`; its `Secure` flag is
   derived from `request.isSecure()`.

The cookies have different purposes and settings. In particular, making
`XSRF-TOKEN` JavaScript-readable does **not** make the session cookie readable.

This is an **exact bean excerpt** from `SecurityConfig.java`:

```java
@Bean
CsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository =
        CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));
    return repository;
}
```

`csrf.spa()` installs Spring Security's SPA request handling. The repository is
then explicitly assigned so the filter chain and login-time
`CsrfAuthenticationStrategy` share the same repository:

```java
// Abridged excerpt from securityFilterChain(); not standalone code.
.csrf(csrf -> {
    csrf.spa();
    csrf.csrfTokenRepository(csrfTokenRepository);
})
```

CSRF token generation is deferred. `GET /api/auth/csrf` forces token emission,
and the SPA bootstraps a new token when necessary, including after login or
logout rotates or clears the previous token.

## Authorization rules

Public access is narrowly defined. Most authentication endpoints are listed
individually rather than exposing all of `/api/auth/**`. The one intentional
subtree wildcard is `/api/auth/password-reset/**`, because both reset request and
reset confirmation must be callable before authentication.

The following is an **exact authorization excerpt** from
`SecurityConfig.java`:

```java
.authorizeHttpRequests(auth -> auth
    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
    .requestMatchers("/api/auth/register", "/api/auth/login",
        "/api/auth/csrf", "/api/auth/me",
        "/api/auth/password-reset/**").permitAll()
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .requestMatchers("/actuator/health").permitAll()
    .anyRequest().authenticated())
```

The `ERROR` dispatcher is permitted so container error dispatches retain their
real status. `/actuator/health` remains public for operator probes. All other
unmatched requests require authentication, and unauthenticated access uses an
HTTP 401 entry point rather than a login redirect.

Logout is `POST /api/auth/logout`. CSRF remains enabled for it; the configured
logout handlers invalidate the server-side session, clear the CSRF token, and
send a clearing `SESSION` cookie.

## CORS

CORS is enabled with credentials and an exact origin allow-list. Allowed methods
are `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, and `OPTIONS`; allowed request
headers are `Content-Type`, `X-XSRF-TOKEN`, and `Authorization`.

Production binds the origin list from `APP_CORS_ALLOWED_ORIGINS`:

```yaml
# Exact excerpt from application-prod.yml
app:
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:}
```

The trailing `:` supplies a blank default. Therefore this variable is **not a
startup requirement**: when it is absent, the application starts with no allowed
origins. A separately hosted browser SPA then cannot make credentialed
cross-origin API calls. Set it to the exact deployed SPA origin or origins; do
not use `*` with credentialed CORS.

## Security response headers

The API configures a restrictive content security policy, referrer policy, and
permissions policy:

```java
// Exact excerpt from SecurityConfig.java
.headers(headers -> headers
    .contentSecurityPolicy(csp -> csp.policyDirectives(
        "default-src 'none'; frame-ancestors 'none'"))
    .referrerPolicy(referrer -> referrer.policy(
        ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
    .permissionsPolicy(permissions -> permissions.policy(
        "camera=(), microphone=(), geolocation=()")))
```

This CSP is suitable for a JSON-only API; it is not a CSP for hosting the React
application. `STRICT_ORIGIN_WHEN_CROSS_ORIGIN` prevents the full path and query
string—such as a reset token—from being sent in the `Referer` header to a
different origin. It does not suppress the full referrer on same-origin
navigation, so reset pages should also avoid loading or linking to untrusted
same-origin content.

Spring Security's default headers remain enabled. HSTS is emitted only for
requests Spring considers secure, which is one reason forwarded-header handling
must match the proxy deployment.

## Brute-force defense

Login applies the per-IP throttle before account state or credential checks,
then applies account lockout and credential verification. A throttled request
returns HTTP 429 and does not reach password verification.

The anti-denial-of-service invariant is:

```text
app.ip-throttle.max-failures < app.lockout.max-failures
```

With the defaults, a single source IP is throttled at four failures before it
can reach the five failures needed to lock an account. A startup validator
rejects configuration that violates this relationship. Unknown usernames, bad
passwords, and locked or disabled accounts return the same generic 401 response
to reduce account-state disclosure.

## Password reset

Reset tokens are 32 random bytes, and only each token's SHA-256 hash is stored.
Tokens expire after 15 minutes and are single-use. A successful reset changes
the password, atomically consumes the token, invalidates sibling reset tokens,
and removes all sessions belonging to that user.

Unknown email addresses receive the same reset-request response as known
addresses. Invalid, expired, and already-used tokens receive the same generic
HTTP 400 response.

Production disables reset-link logging and has no localhost URL fallback:

```yaml
# Exact excerpt from application-prod.yml
app:
  password-reset:
    log-reset-link: false
    link-base-url: ${APP_RESET_LINK_BASE_URL:}
```

`APP_RESET_LINK_BASE_URL` is also **not a startup requirement** because its
default is blank. It is operationally required before a real email delivery
implementation can produce usable links. `log-reset-link: false` ensures the
stub email service does not place plaintext bearer tokens in production logs.

## Audit logging

Security events are emitted as JSON through the dedicated `audit` logger.
Transactional events are deferred until `afterCommit`, avoiding a success audit
record for a mutation that later rolls back. Events include login success and
failure, account lockout, password-reset request and completion, and
administrator status, role, and deletion actions. Passwords, reset tokens, and
other secrets must not be included in audit fields.

## Production profile

`backend/src/main/resources/application-prod.yml` defines the hardened profile.
The following is an **abridged but value-for-value excerpt**; unrelated comments
are omitted:

```yaml
spring:
  datasource:
    embedded-database-connection: none
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.yaml
  session:
    jdbc:
      initialize-schema: never
  sql:
    init:
      mode: never
  h2:
    console:
      enabled: false

server:
  forward-headers-strategy: framework
  servlet:
    session:
      cookie:
        secure: true
        http-only: true
        same-site: strict

app:
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:}
  password-reset:
    log-reset-link: false
    link-base-url: ${APP_RESET_LINK_BASE_URL:}
  admin:
    username: ${APP_ADMIN_USERNAME:}
    password: ${APP_ADMIN_PASSWORD:}
    email: ${APP_ADMIN_EMAIL:admin@localhost}
```

Key effects are:

- No embedded database fallback; the external datasource is mandatory.
- Liquibase owns schema changes, while Hibernate only validates the schema.
- Spring SQL and Spring Session schema initialization are disabled to avoid
  competing with Liquibase.
- The H2 console is disabled, and its permissive filter chain exists only under
  the `dev` profile.
- The production `SESSION` cookie is HTTPS-only, HttpOnly, and Strict SameSite.
- Reset links are not logged.
- Trusted forwarding headers are interpreted by the framework.

### Environment variables

| Variable | Startup status | Purpose and behavior when absent |
|---|---|---|
| `DB_URL` | Required | External PostgreSQL or MySQL JDBC URL; no embedded fallback |
| `DB_USERNAME` | Required | Datasource username |
| `DB_PASSWORD` | Required | Datasource password |
| `APP_ADMIN_USERNAME` | Required by the prod admin validator | Initial administrator username |
| `APP_ADMIN_PASSWORD` | Required by the prod admin validator | Initial administrator password; must meet `app.password-min-length` |
| `APP_ADMIN_EMAIL` | Optional | Defaults to `admin@localhost` |
| `APP_CORS_ALLOWED_ORIGINS` | Optional at startup; normally required operationally | Blank means no cross-origin browser caller is allowed |
| `APP_RESET_LINK_BASE_URL` | Optional at startup; required for usable delivered reset links | Blank means no configured reset-page base URL |

Database placeholders do not have defaults, while the admin placeholders do.
The production-specific `ProdAdminCredentialsValidator` supplies explicit
fail-fast checks for blank admin username/password and an undersized admin
password. Separately, `SecurityTunablesValidator` rejects an IP-throttle limit
that is greater than or equal to the account-lockout limit. Do not generalize
these targeted checks into a claim that every blank production setting prevents
startup: CORS and reset-link base URL deliberately default to blank.

Start a previously built JAR from the repository root with:

```text
java -jar backend/target/hello-auth-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Supply secrets through the deployment platform's secret mechanism rather than
putting them in the command line, shell history, or source-controlled files.

### Trusted proxy requirement

`server.forward-headers-strategy: framework` trusts supported forwarding headers
when deriving request properties such as scheme and remote address. Enable this
configuration only behind a trusted proxy that removes client-supplied
forwarding headers and writes authoritative values.

That deployment contract is necessary for three reasons:

- `request.isSecure()` must reflect the original HTTPS request so secure-only
  behavior, including HSTS and the derived `XSRF-TOKEN` Secure flag, is correct.
- `getRemoteAddr()` must identify the client rather than collapsing every user
  into the proxy's IP-throttle bucket.
- Untrusted forwarding headers must not let a directly connected client spoof
  its source address and evade IP throttling.

`Secure` cookies protect transport only when users reach the application over
HTTPS. The configuration does not itself provision certificates or a TLS
listener.

## Key files

| File | Responsibility |
|---|---|
| `backend/src/main/java/com/example/helloauth/config/SecurityConfig.java` | Filter chains, authorization, CSRF, CORS, headers, authentication, and cookie customization |
| `backend/src/main/resources/application-prod.yml` | Production datasource, schema, proxy, cookie, CORS, reset, and admin settings |
| `backend/src/main/resources/application.yml` | Shared session timeout and security tunable defaults |
| `backend/src/main/java/com/example/helloauth/config/AppProperties.java` | Typed `app.*` properties |
| `backend/src/main/java/com/example/helloauth/config/ProdAdminCredentialsValidator.java` | Production admin credential validation |
| `backend/src/main/java/com/example/helloauth/config/SecurityTunablesValidator.java` | IP-throttle/account-lockout invariant |
| `backend/src/main/java/com/example/helloauth/auth/LoginService.java` | Ordered login defenses |
| `backend/src/main/java/com/example/helloauth/passwordreset/PasswordResetService.java` | Single-use reset workflow |
| `backend/src/main/java/com/example/helloauth/audit/AuditLogger.java` | Commit-aware JSON security events |
| `frontend/src/lib/api.ts` | Credentialed fetch and CSRF header contract |
