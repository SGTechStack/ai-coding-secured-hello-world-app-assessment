# 05 — Pin down the Spring Security 7 / Spring Session config surface on Boot 4.1

Type: research
Status: resolved
Blocked by: —

## Question

On Spring Boot 4.1.x with Spring Framework 7 and Spring Security 7, what is the *actual, current*
configuration API for everything this app needs — verified against official documentation rather
than recalled from Spring Boot 3.x material?

This ticket exists because of an honest limitation: Boot 4 / Security 7 config surfaces differ from
the Boot 3 material that dominates available examples, and guessing here produces code that looks
right and does not compile, or worse, compiles and silently fails to enforce something.

## What to pin down

From official Spring documentation for the 4.1.x / Security 7 line:

1. **`SecurityFilterChain` construction** — current DSL, what changed from 6.x, what is removed.
2. **Session-bound CSRF** — configuring `HttpSessionCsrfTokenRepository` (the App Standard's
   `[Enforced Constraint]`; `CookieCsrfTokenRepository` is prohibited), plus the current handler
   situation (`CsrfTokenRequestAttributeHandler`, BREACH protection, deferred token loading) and how
   a dedicated endpoint returns the token without caching.
3. **Spring Session JDBC** — starter coordinates and version for Boot 4.1, the DDL script location
   and whether we own it in Flyway or let Spring Session initialise it, and the property names for
   the table name and cleanup cron.
4. **`SpringSessionBackedSessionRegistry`** — exact wiring so "invalidate every session for this
   user" works, plus how it composes with `sessionManagement().maximumSessions(1)`.
5. **Session timeouts** — how idle timeout (`server.servlet.session.timeout`) and an **absolute**
   session lifetime are both configured. Spring Session has no first-class absolute-lifetime
   setting; find the supported mechanism rather than inventing one.
6. **Cookie attributes** — setting `HttpOnly`, `Secure`, `SameSite=Lax` for both the session cookie
   and the CSRF cookie, per-profile so local HTTP dev still works with `Secure` off.
7. **JSON login** — the App Standard notes form login accepts
   `application/x-www-form-urlencoded` by default and that JSON requires a custom
   `AuthenticationFilter`. What is the current recommended approach on Security 7?
8. **Security headers** — current DSL for HSTS, `X-Content-Type-Options`, `X-Frame-Options: DENY`,
   and `Content-Security-Policy: default-src 'self'`.
9. **CORS** — current `CorsConfigurationSource` wiring with an explicit allowlist and
   `allowCredentials=true`, and how it interacts with the security filter chain ordering.
10. **Method security** — `@EnableMethodSecurity` and `@PreAuthorize` on Security 7.
11. **Breaking changes** that bite this app: Jakarta EE 11 / Servlet 6.1, Tomcat 11, anything
    removed that the recipes in "Inventory the prescribed recipes" assume.

## Method

Official Spring Boot 4.1 and Spring Security 7 reference documentation and release notes. Where
documentation is thin, say so explicitly and mark the item as needing a throwaway compile check
during the build phase rather than asserting a guess.

## Done when

Each of the eleven items has a verified, citable answer, and anything that could not be verified is
listed as an open risk for the build phase instead of being quietly filled in.

---

## Answer

### Verification basis and honesty statement

Everything below was checked against one of: the current Spring Security reference docs
(`docs.spring.io/spring-security/reference` — the unversioned path currently serves the **7.1**
line), the current Spring Session reference docs, the current Spring Boot reference docs, the
Spring Boot release-notes/migration-guide wikis, published `spring.io/security` advisories, or the
`main` branch source of `spring-projects/spring-security`, `spring-projects/spring-session` and
`spring-projects/spring-boot` on GitHub.

Managed versions confirmed from Boot's own dependency-versions appendix
([dependency versions — coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)):

| Artifact | Managed version |
|---|---|
| `org.springframework.security:spring-security-*` | **7.1.1** |
| `org.springframework.session:spring-session-core` / `spring-session-jdbc` | **4.1.1** |

Two honesty caveats up front:

1. **Source-derived vs doc-derived.** Where the reference docs were silent I read the `main`-branch
   source. `main` is ahead of 7.1.1 in general. Anything I flag as "source-derived" is *behaviour I
   read in code*, not a documented contract — it can change in a patch release and it deserves a
   throwaway compile/behaviour check in the build phase. Anything doc-derived is safe to treat as
   API.
2. **Things I could not verify are listed explicitly** in the "Open risks" section at the end. I
   have not filled any gap with a plausible-sounding guess. In particular the *exact* removal list
   for Security 7 (item 1/item 11) is **incomplete**: the `migration-7` pages have been removed from
   the 7.x docs (they now 404) and the GA release notes on GitHub only list deltas since RC1. The
   best surviving official removal list is the `6.5` line's "Preparing for 7.0" pages, which I used.

---

### 1. `SecurityFilterChain` construction — current DSL, what changed, what is removed

**Still the same shape:** a `@Configuration` class annotated `@EnableWebSecurity` exposing a
`SecurityFilterChain` bean built from `HttpSecurity`. Every code sample in the 7.1 reference docs
uses exactly this ([CSRF chapter](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html),
[headers chapter](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html)).

**Confirmed changes from 6.x that bite us:**

- **The lambda DSL is now mandatory.** The pre-lambda chained style (`.authorizeHttpRequests()` …
  `.and()` … ) is stated to not be valid in 7
  ([Configuration Migrations, 6.5 docs](https://docs.spring.io/spring-security/reference/6.5/migration-7/configuration.html)).
  Practically: no `.and()`, every configurer takes a `Customizer` lambda or
  `Customizer.withDefaults()`.
- **`AntPathRequestMatcher` and `MvcRequestMatcher` are no longer supported**; the DSL uses
  `PathPatternRequestMatcher` and **requires absolute URIs** (less any context root). Servlet-path
  inference is gone — if you need a prefix you state it:
  `PathPatternRequestMatcher.withDefaults().basePath("/mvc")`
  ([Web Migrations](https://docs.spring.io/spring-security/reference/6.5/migration-7/web.html)).
  The 7.1 docs use `PathPatternRequestMatcher.withDefaults().matcher("/logout")` throughout, which
  corroborates this.
- **`PortResolver` is removed** (it existed only as an old Internet Explorer workaround) — same
  source.
- **`requiresChannel(...)` is replaced by `redirectToHttps(...)`** — same source. Relevant to us
  because "force HTTPS in prod" is a natural instinct; the 7 way is
  `http.redirectToHttps((https) -> https.requestMatchers(...))`.
- **Login redirects are now relative.** Security 7 emits `Location: /my-login` instead of an
  absolute URI; `LoginUrlAuthenticationEntryPoint#setFavorRelativeUris(false)` restores 6.x
  behaviour — same source.
- **`DaoAuthenticationProvider` has no no-arg constructor.** On `main` the only constructor is
  `DaoAuthenticationProvider(UserDetailsService)`, and `retrieveUser` is `protected final`
  ([source](https://raw.githubusercontent.com/spring-projects/spring-security/main/core/src/main/java/org/springframework/security/authentication/dao/DaoAuthenticationProvider.java)).
  The Boot 3 habit of `new DaoAuthenticationProvider(); p.setUserDetailsService(…)` will not
  compile.
- **New (source-derived, matters for our authority model):**
  `AbstractUserDetailsAuthenticationProvider#createSuccessAuthentication` now *adds*
  `FactorGrantedAuthority.PASSWORD_AUTHORITY` to the resulting authorities
  ([source](https://raw.githubusercontent.com/spring-projects/spring-security/main/core/src/main/java/org/springframework/security/authentication/dao/AbstractUserDetailsAuthenticationProvider.java)).
  So a successfully authenticated principal carries an extra factor authority alongside its roles.
  **Any test that asserts an exact authority set, or any `@PreAuthorize` that enumerates
  authorities, must account for this.** Flagged as a build-phase check.
- **New:** `AbstractAuthenticationProcessingFilter` and `AuthenticationFilter` both expose
  `setMfaEnabled(boolean)` (default `false`). We leave it off.

Skeleton (doc-shaped, safe):

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests((authorize) -> authorize
                .requestMatchers("/csrf", "/login").permitAll()
                .anyRequest().authenticated()
            )
            .csrf((csrf) -> csrf
                .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
            )
            .sessionManagement((session) -> session
                .sessionFixation((fixation) -> fixation.changeSessionId())
                .maximumSessions(1)
            )
            .headers((headers) -> headers
                .httpStrictTransportSecurity((hsts) -> hsts
                    .includeSubDomains(true).maxAgeInSeconds(31536000))
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions((frame) -> frame.deny())
                .contentSecurityPolicy((csp) -> csp.policyDirectives("default-src 'self'"))
            )
            .cors(Customizer.withDefaults())
            .logout((logout) -> logout
                .addLogoutHandler(new HeaderWriterLogoutHandler(
                        new ClearSiteDataHeaderWriter(Directive.COOKIES)))
            );
        return http.build();
    }
}
```

---

### 2. Session-bound CSRF: `HttpSessionCsrfTokenRepository`, handler, BREACH, deferred loading, `/csrf` endpoint

All from the [CSRF chapter](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

**Repository.** `HttpSessionCsrfTokenRepository` is still the **default** — no code needed — but the
docs give the explicit form, which is what we want for an auditable `[Enforced Constraint]`:

```java
.csrf((csrf) -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
```

`setSessionAttributeName(String)` exists if we ever need to read the attribute directly.

**Handler / BREACH.** The default handler is `XorCsrfTokenRequestAttributeHandler`, which provides
BREACH protection by encoding per-request randomness into the emitted token value; the raw token is
recovered on validation. `CsrfTokenRequestAttributeHandler` exists **only** to opt *out* of BREACH
protection. **Keep the Xor handler.** Both resolve the submitted token from header `X-CSRF-TOKEN`
or `X-XSRF-TOKEN`, or request parameter `_csrf`, by default.

Consequence for our error contract: the value returned by `/csrf` changes on every call. The client
must not cache it and must not compare it across responses.

**Deferred loading.** Deferred is the default and is the whole reason session-backed CSRF is cheap:
the `HttpSession` is not read on every request. Do **not** opt out. Note the documented way to opt
out is setting `csrfRequestAttributeName` to `null` — so **never** set that to null.

**Do NOT use `csrf.spa()`.** `CsrfConfigurer#spa()` exists in 7.1
([CsrfConfigurer javadoc](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/config/annotation/web/configurers/CsrfConfigurer.html))
and looks attractive for a JS frontend, but it is the packaged single-page-application preset built
on `CookieCsrfTokenRepository`. Cookie-based CSRF storage is prohibited for us, so `spa()` is
prohibited too. This is the single most likely wrong turn on this ticket.

**Dedicated token endpoint.** The docs describe exactly the pattern we need and call it out as
applicable to any CSRF-protected app, not just mobile:

```java
@RestController
public class CsrfController {

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }
}
```

with `.requestMatchers("/csrf").permitAll()` if it must be callable before authentication. The docs
state the client should call it at load time **and again after authentication success and after
logout success**, because `CsrfAuthenticationStrategy` and `CsrfLogoutHandler` clear the previous
token.

**No-cache headers come for free.** Spring Security's default header set includes cache control.
`CacheControlHeadersWriter` writes `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`,
`Pragma: no-cache` and `Expires: 0`, and it **skips** writing if the response already carries
`Cache-Control`, `Expires` or `Pragma`, or if the status is 304
([source](https://raw.githubusercontent.com/spring-projects/spring-security/main/web/src/main/java/org/springframework/security/web/header/writers/CacheControlHeadersWriter.java)).
So as long as we do not disable `headers().cacheControl()` and do not set our own `Cache-Control` on
that handler, `/csrf` is already uncacheable. If you want it explicit and self-documenting rather
than implicit, return `ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(csrfToken)` —
but be aware that setting it yourself *suppresses* Spring Security's three-header set for that
response, so you then also lose `Pragma`/`Expires`. My recommendation: rely on the default and
assert the three headers in a test.

**Interaction with Spring Session JDBC (important).** With session-backed CSRF the token becomes a
session attribute, so it is serialised into `SPRING_SESSION_ATTRIBUTES`. With the default JDK
serialization that is fine. If we ever switch attribute storage to JSON, the Spring Session docs
require registering Spring Security's Jackson modules — and their example still uses the Jackson 2
`ObjectMapper` and `SecurityJackson2Modules`, while Boot 4 defaults to Jackson 3 (`tools.jackson`).
**Decision: keep JDK serialization for session attributes.** Do not attempt JSON attribute storage
on Boot 4 without a spike.

---

### 3. Spring Session JDBC on Boot 4.1

**Starter.** Boot 4 introduced a real starter — this is new, Boot 3 had none:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-session-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-session-jdbc-test</artifactId>
    <scope>test</scope>
</dependency>
```

No version — Boot's BOM manages it (`spring-session-jdbc` **4.1.1**). Confirmed by the
[Boot 4.0 migration guide starter table](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
and the [Boot Spring Session reference page](https://docs.spring.io/spring-boot/reference/web/spring-session.html).
You do **not** add `@EnableJdbcHttpSession` — the docs state the servlet auto-configuration replaces
`@Enable*HttpSession`, and adding the annotation makes the auto-configuration (and therefore the
property binding) back off.

**Properties.** Read directly off Boot's `JdbcSessionProperties`
([source](https://raw.githubusercontent.com/spring-projects/spring-boot/main/module/spring-boot-session-jdbc/src/main/java/org/springframework/boot/session/jdbc/autoconfigure/JdbcSessionProperties.java))
and its superclass `DatabaseInitializationProperties`
([source](https://raw.githubusercontent.com/spring-projects/spring-boot/main/module/spring-boot-jdbc/src/main/java/org/springframework/boot/jdbc/init/DatabaseInitializationProperties.java)):

| Property | Default | Note |
|---|---|---|
| `spring.session.jdbc.table-name` | `SPRING_SESSION` | attributes table is always `<tableName>_ATTRIBUTES` |
| `spring.session.jdbc.cleanup-cron` | `0 * * * * *` (every minute) | 6-field Spring cron |
| `spring.session.jdbc.initialize-schema` | `EMBEDDED` | `embedded` / `always` / `never` |
| `spring.session.jdbc.schema` | `classpath:org/springframework/session/jdbc/schema-@@platform@@.sql` | |
| `spring.session.jdbc.platform` | auto-detected | fills `@@platform@@` |
| `spring.session.jdbc.continue-on-error` | `true` | |
| `spring.session.jdbc.flush-mode` | `ON_SAVE` | |
| `spring.session.jdbc.save-mode` | `ON_SET_ATTRIBUTE` | |

**Flyway vs Spring Session init — own it in Flyway.** Two traps make this the right call:
`initialize-schema` defaults to `EMBEDDED`, which means against a real PostgreSQL **Spring Session
creates nothing** and you get a runtime failure on first session write; and `continue-on-error`
defaults to `true`, which can swallow a genuine DDL failure. So:

```properties
spring.session.jdbc.initialize-schema=never
spring.session.jdbc.table-name=SPRING_SESSION
spring.session.jdbc.cleanup-cron=0 */5 * * * *
```

and put the verified DDL in a Flyway migration. This is the shipped PostgreSQL script verbatim
([schema-postgresql.sql](https://raw.githubusercontent.com/spring-projects/spring-session/main/spring-session-jdbc/src/main/resources/org/springframework/session/jdbc/schema-postgresql.sql)):

```sql
CREATE TABLE SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
```

Because we own the DDL, the Flyway migration and the library's expected schema can drift on a Spring
Session upgrade. Add a note to the upgrade checklist to diff `schema-postgresql.sql` on every
Spring Session version bump. Also note `PRINCIPAL_NAME VARCHAR(100)` caps the indexable username
length — worth cross-checking against the username rules in the data-model ticket.

If we also add Flyway, remember Boot 4 requires `spring-boot-starter-flyway` (the bare
`org.flywaydb:flyway-core` dependency is no longer sufficient) — per the Boot 4.0 migration guide.

---

### 4. `SpringSessionBackedSessionRegistry` wiring, and how it composes with `maximumSessions(1)`

**Wiring.** The Spring Session docs give it directly
([Spring Security Integration](https://docs.spring.io/spring-session/reference/spring-security.html)):

```java
@Configuration
public class ConcurrencyConfig {

    @Bean
    SpringSessionBackedSessionRegistry<? extends Session> sessionRegistry(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        return new SpringSessionBackedSessionRegistry<>(sessionRepository);
    }
}
```

`JdbcIndexedSessionRepository` implements `FindByIndexNameSessionRepository`, so the injection works
with the Boot-auto-configured repository.

**You do not have to pass it into the DSL.** Source-derived from `SessionManagementConfigurer`
([source](https://raw.githubusercontent.com/spring-projects/spring-security/main/config/src/main/java/org/springframework/security/config/annotation/web/configurers/SessionManagementConfigurer.java)):
`getSessionRegistry(...)` first uses an explicitly configured registry, otherwise looks up a
`SessionRegistry` **bean**, and only falls back to `new SessionRegistryImpl()` if neither exists.
Publishing the bean above is therefore enough. Passing it explicitly is still clearer and costs
nothing:

```java
.sessionManagement((session) -> session
    .maximumSessions(1)
    .sessionRegistry(sessionRegistry)
)
```

Note the DSL shape: on Security 7.1 the Java DSL puts `maximumSessions` / `maxSessionsPreventsLogin`
**directly on `sessionManagement`** (only the Kotlin DSL nests them under `sessionConcurrency`) —
see the [session management chapter](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html).
There is also a `maximumSessions(Function<Authentication, Integer>)` / `SessionLimit` overload
(since 6.5) if the admin module ever needs a different cap per role.

**How the composition actually works** (source-derived, `SessionManagementConfigurer`): when
concurrency control is enabled the DSL builds a `CompositeSessionAuthenticationStrategy` over, in
order, `ConcurrentSessionControlAuthenticationStrategy` (configured with the session limit and the
`exceptionIfMaximumExceeded` flag), then the session-fixation strategy, then
`RegisterSessionAuthenticationStrategy`. With `maxSessionsPreventsLogin(false)` (the default),
`ConcurrentSessionControlAuthenticationStrategy#allowableSessionsExceeded` sorts the principal's
unexpired sessions by last-request time and calls `expireNow()` on the oldest
([source](https://raw.githubusercontent.com/spring-projects/spring-security/main/web/src/main/java/org/springframework/security/web/authentication/session/ConcurrentSessionControlAuthenticationStrategy.java)).

`expireNow()` on `SpringSessionBackedSessionInformation` sets a marker attribute on the Spring
Session `Session` and saves it — it does **not** delete the row
([source](https://raw.githubusercontent.com/spring-projects/spring-session/main/spring-session-core/src/main/java/org/springframework/session/security/SpringSessionBackedSessionInformation.java)).
The displaced user is only actually logged out when their **next** request reaches
`ConcurrentSessionFilter`, which sees the expired `SessionInformation`.

**Two consequences to design around:**

- **"Invalidate every session belonging to this user" has two flavours.** Soft: resolve
  `sessionRegistry.getAllSessions(principal, false)` and call `expireNow()` on each — cluster-safe,
  but revocation lands on the victim's next request. Hard/immediate: go to the repository:
  `sessionRepository.findByPrincipalName(username).keySet().forEach(sessionRepository::deleteById)`
  — the row is gone, so the cookie is dead immediately everywhere. For an admin "force logout"
  control and for password-change/lock flows, **use the hard delete**; use `expireNow()` only where
  you specifically want the `ConcurrentSessionFilter` expiry UX.
- **`HttpSessionEventPublisher` is not needed and does nothing useful here.** The docs tell you to
  register it for concurrency control, but that instruction exists for the in-memory
  `SessionRegistryImpl`. `SpringSessionBackedSessionRegistry.registerNewSession`,
  `removeSessionInformation` and `refreshLastRequest` are all no-ops
  ([source](https://raw.githubusercontent.com/spring-projects/spring-session/main/spring-session-core/src/main/java/org/springframework/session/security/SpringSessionBackedSessionRegistry.java)),
  so there is nothing for the publisher to feed. Registering it is harmless; relying on it is not.
  Also `getAllPrincipals()` **throws `UnsupportedOperationException`** — do not build an admin
  "all active sessions" screen on the `SessionRegistry`; query `SPRING_SESSION` instead.
- **The `PRINCIPAL_NAME` index must be populated** or `findByPrincipalName` returns nothing and
  `maximumSessions(1)` silently never triggers. Spring Session derives it from the session's
  security context. This is the classic silent-failure mode on this feature: **write an integration
  test that logs in twice and asserts the first session is dead.** Marked as a required build-phase
  test, not an assumption.

---

### 5. Idle timeout AND absolute session lifetime

**Idle timeout — supported, straightforward.** `spring.session.timeout` is the Spring Session
property; if unset in a servlet app the auto-configuration falls back to
`server.servlet.session.timeout`
([Boot Spring Session page](https://docs.spring.io/spring-boot/reference/web/spring-session.html)).
Boot's `SessionAutoConfiguration` implements exactly that fallback and also lets you supply a
`SessionTimeout` bean to compute it programmatically
([source](https://raw.githubusercontent.com/spring-projects/spring-boot/main/module/spring-boot-session/src/main/java/org/springframework/boot/session/autoconfigure/SessionAutoConfiguration.java)).
Set one place and be done:

```properties
spring.session.timeout=15m
```

**Absolute lifetime — there is no first-class setting. Plainly: it does not exist.** I verified this
rather than inferring it:

- `spring.session.*` binds to Boot's `SessionProperties`, whose *only* timeout-ish field is
  `timeout`; the rest is `servlet.filter-order` and `servlet.filter-dispatcher-types`
  ([source](https://raw.githubusercontent.com/spring-projects/spring-boot/main/module/spring-boot-session/src/main/java/org/springframework/boot/session/autoconfigure/SessionProperties.java)).
- `spring.session.jdbc.*` binds to `JdbcSessionProperties` — table name, cleanup cron, schema init,
  flush/save mode. Nothing about lifetime.
- Spring Session's `Session` interface exposes `getCreationTime()`, `getLastAccessedTime()`,
  `setMaxInactiveInterval(Duration)` / `getMaxInactiveInterval()` and `isExpired()` — and that is
  the entire timeout surface. `maxInactiveInterval` is by definition idle-only
  ([Session.java](https://raw.githubusercontent.com/spring-projects/spring-session/main/spring-session-core/src/main/java/org/springframework/session/Session.java)).
- The JDBC schema stores `CREATION_TIME` and `EXPIRY_TIME`, where `EXPIRY_TIME` is derived from
  last access + max inactive interval. There is no absolute-expiry column.

So an absolute lifetime has to be **built**, on top of documented public API. Two layers, and you
want both:

**(a) Client-side hint — a real, supported Boot property.** `server.servlet.session.cookie.max-age`
is mapped onto `DefaultCookieSerializer#setCookieMaxAge` by Boot's `SessionAutoConfiguration`
(verified in the source above), and `DefaultCookieSerializer#writeCookieValue` turns that into
`Max-Age=` plus a matching `Expires=`
([source](https://raw.githubusercontent.com/spring-projects/spring-session/main/spring-session-core/src/main/java/org/springframework/session/web/http/DefaultCookieSerializer.java)).
This is genuinely an absolute clock, but it is **advisory only** — a hostile client just keeps
sending the cookie. Never rely on it alone.

**(b) Server-side enforcement — this is the one that counts.** Stamp an authentication timestamp on
the session at login and reject past it.

Do **not** enforce against `HttpSession#getCreationTime()`. Reason, source-derived: Spring Session's
`changeSessionId()` rotates the id on the *same* `Session` object, so creation time survives session
fixation protection. The session usually already exists before login (the CSRF token created it), so
`getCreationTime()` measures "time since the browser first touched us", not "time since login". That
is a subtly wrong absolute clock.

Instead add an extra `SessionAuthenticationStrategy`. `SessionManagementConfigurer` exposes
`addSessionAuthenticationStrategy(...)`, which appends into the composite rather than replacing the
fixation/concurrency strategies (source-derived, same file):

```java
// runs after successful authentication, inside the composite strategy
public final class AuthInstantStampingStrategy implements SessionAuthenticationStrategy {

    public static final String AUTH_INSTANT = AuthInstantStampingStrategy.class.getName() + ".AUTH_INSTANT";

    @Override
    public void onAuthentication(Authentication authentication,
            HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession();
        session.setAttribute(AUTH_INSTANT, Instant.now().toEpochMilli());
    }
}
```

```java
public final class AbsoluteSessionLifetimeFilter extends OncePerRequestFilter {

    private final Duration maxLifetime;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Long authInstant = (Long) session.getAttribute(AuthInstantStampingStrategy.AUTH_INSTANT);
            if (authInstant != null
                    && Instant.ofEpochMilli(authInstant).plus(this.maxLifetime).isBefore(Instant.now())) {
                session.invalidate();
                SecurityContextHolder.clearContext();
                // emit the app's standard 401 envelope here
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
```

Register it in the chain after the security-context filter so `SecurityContextHolder` is populated
(`http.addFilterAfter(...)`). **Both snippets in (b) are design, not verified library behaviour** —
mark them for a throwaway compile check plus an integration test with a 2-second lifetime. What *is*
verified is that the hooks exist (`addSessionAuthenticationStrategy`, `SessionAuthenticationStrategy`,
`OncePerRequestFilter`) and that nothing in the library does this for us.

Ordering note: because the absolute filter invalidates rather than just rejecting, the session row
disappears and the stale cookie behaves like any other invalid session — `invalidSessionUrl` /
`invalidSessionStrategy` on `sessionManagement` is the documented hook if we want a specific
response there.

---

### 6. JSON login body on Security 7

**Form login is still form-encoded only.** Verified two ways:
`UsernamePasswordAuthenticationFilter#obtainUsername/obtainPassword` read
`request.getParameter(...)`, full stop
([source](https://raw.githubusercontent.com/spring-projects/spring-security/main/web/src/main/java/org/springframework/security/web/authentication/UsernamePasswordAuthenticationFilter.java));
and `FormLoginConfigurer` offers `usernameParameter` / `passwordParameter` / `loginPage` /
`loginProcessingUrl` and **no** converter or content-type hook
([javadoc](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/config/annotation/web/configurers/FormLoginConfigurer.html)).
The App Standard's note still holds on 7.

There are three routes. The choice hinges on one thing: **whether `SessionAuthenticationStrategy`
runs**, because that is what gives us session-fixation protection and `maximumSessions(1)`.

**Route A — controller endpoint. Documented, but loses the session strategy.** The Security 7.1
docs show this verbatim under "Storing the Authentication manually": a `@PostMapping("/login")`
taking `@RequestBody LoginRequest`, calling `authenticationManager.authenticate(...)`, then
`securityContextRepository.saveContext(context, request, response)` with an
`HttpSessionSecurityContextRepository`
([session management chapter](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)).
It is official and it works — but it is a controller, so no filter-level
`SessionAuthenticationStrategy` fires. You would have to call `request.changeSessionId()` and drive
concurrency control yourself. The same chapter warns: a customised authentication filter for
form-based login means configuring concurrent session control explicitly.

**Route B — `AuthenticationFilter` with an `AuthenticationConverter`.** `AuthenticationFilter` takes
`(AuthenticationManager, AuthenticationConverter)`, and exposes `setRequestMatcher`,
`setSecurityContextRepository`, `setSuccessHandler`, `setFailureHandler`. It has **no**
`setSessionAuthenticationStrategy`
([javadoc](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/web/authentication/AuthenticationFilter.html)).
Same gap as Route A.

**Route C — recommended. Extend `AbstractAuthenticationProcessingFilter` and give it a JSON
`AuthenticationConverter`.** This is the Security 7 improvement that makes JSON login clean:
`attemptAuthentication` is **no longer abstract** — it now delegates to a configurable
`AuthenticationConverter`, and the default converter throws a message telling you to either supply a
converter or override the method
([source](https://raw.githubusercontent.com/spring-projects/spring-security/main/web/src/main/java/org/springframework/security/web/authentication/AbstractAuthenticationProcessingFilter.java);
`setAuthenticationConverter` is public per the
[javadoc](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/web/authentication/AbstractAuthenticationProcessingFilter.html)).
Crucially `doFilter` still calls `this.sessionStrategy.onAuthentication(...)` on success, and
`setSessionAuthenticationStrategy` is public. So this route keeps session fixation protection and
concurrency control.

```java
public final class JsonUsernamePasswordAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    public JsonUsernamePasswordAuthenticationFilter(AuthenticationManager authenticationManager,
            AuthenticationConverter converter) {
        super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login"),
                authenticationManager);
        setAuthenticationConverter(converter);
        setSecurityContextRepository(new HttpSessionSecurityContextRepository());
    }
}
```

with a converter that reads the JSON body and returns
`UsernamePasswordAuthenticationToken.unauthenticated(username, password)` (returning `null` for a
non-JSON or unparseable body, which makes the filter fall through rather than 500), and explicit
strategy wiring:

```java
SessionAuthenticationStrategy strategy = new CompositeSessionAuthenticationStrategy(List.of(
        concurrentSessionControl,                       // ConcurrentSessionControlAuthenticationStrategy(sessionRegistry)
        new ChangeSessionIdAuthenticationStrategy(),
        new RegisterSessionAuthenticationStrategy(sessionRegistry),
        new AuthInstantStampingStrategy()));           // item 5(b)
filter.setSessionAuthenticationStrategy(strategy);
```

That composite order mirrors exactly what `SessionManagementConfigurer` builds for the DSL
(source-derived, cited in item 4). Register with
`http.addFilterAt(filter, UsernamePasswordAuthenticationFilter.class)` and set JSON-emitting
success/failure handlers so the error envelope stays consistent (no redirects).

**Route C is a design assembled from verified primitives, not a documented recipe.** Every class,
constructor and setter used above is confirmed present, but the combination is ours. Flag it for a
throwaway compile check and an integration test covering: JSON login succeeds; session id changes;
a second login kills the first session; a bad body yields our standard envelope not a 500.

Two smaller notes: the request body must be readable once only — the converter consuming the body
means downstream filters cannot re-read it, which is fine because the filter terminates the request
on both success and failure. And CSRF still applies to `POST /login`: the docs are explicit that
Spring Security requires a CSRF token on login requests to prevent login CSRF, so the client must
fetch `/csrf` first.

---

### 7. Cookie attributes per profile, and the `__Host-` prefix

**How Boot and Spring Session connect — verified.** Boot's `SessionAutoConfiguration` creates a
`DefaultCookieSerializer` and maps Boot's `server.servlet.session.cookie.*` onto it
([source](https://raw.githubusercontent.com/spring-projects/spring-boot/main/module/spring-boot-session/src/main/java/org/springframework/boot/session/autoconfigure/SessionAutoConfiguration.java)):

| Boot property | `DefaultCookieSerializer` setter |
|---|---|
| `server.servlet.session.cookie.name` | `setCookieName` |
| `server.servlet.session.cookie.domain` | `setDomainName` |
| `server.servlet.session.cookie.path` | `setCookiePath` |
| `server.servlet.session.cookie.http-only` | `setUseHttpOnlyCookie` |
| `server.servlet.session.cookie.secure` | `setUseSecureCookie` |
| `server.servlet.session.cookie.max-age` | `setCookieMaxAge` (seconds) |
| `server.servlet.session.cookie.same-site` | `setSameSite(sameSite.attributeValue())` |
| `server.servlet.session.cookie.partitioned` | `setPartitioned` |

Accepted `same-site` values are `omitted`, `none`, `lax`, `strict`, from Boot's `Cookie.SameSite`
enum ([source](https://raw.githubusercontent.com/spring-projects/spring-boot/main/module/spring-boot-web-server/src/main/java/org/springframework/boot/web/server/Cookie.java)).

Three defaults worth knowing, from `DefaultCookieSerializer`
([source](https://raw.githubusercontent.com/spring-projects/spring-session/main/spring-session-core/src/main/java/org/springframework/session/web/http/DefaultCookieSerializer.java)):
`useHttpOnlyCookie` is `true`; `sameSite` is **`"Lax"`**; and `useSecureCookie` is unset, meaning
Secure tracks `HttpServletRequest#isSecure()` per request. Also, Boot 4's `PropertyMapper` no longer
invokes setters for `null` sources (per the Boot 4.0 migration guide), so **unset properties leave
the Spring Session defaults in place, not Tomcat's.** That is why `same-site=strict` must be set
explicitly — omitting it gives you Lax, silently.

The `useSecureCookie` default also means: behind a TLS-terminating proxy, `isSecure()` is false
unless forwarded-header handling is on, so the Secure flag would silently drop. Set it explicitly in
prod rather than relying on the default.

**Two caveats on customising.** Boot only creates its property-mapped `DefaultCookieSerializer`
under `DefaultCookieSerializerCondition` — if you publish your own `CookieSerializer` bean, Boot
backs off and **all** the property mapping above is lost. To adjust something without losing it, use
the callback interface instead:

```java
@Bean
DefaultCookieSerializerCustomizer sessionCookieCustomizer() {
    return (serializer) -> serializer.setUseBase64Encoding(true);
}
```

(`DefaultCookieSerializerCustomizer` is public API in `org.springframework.boot.session.autoconfigure`,
[source](https://raw.githubusercontent.com/spring-projects/spring-boot/main/module/spring-boot-session/src/main/java/org/springframework/boot/session/autoconfigure/DefaultCookieSerializerCustomizer.java)).
Boot itself uses one of these to wire remember-me support, so ours composes rather than replaces.

**`__Host-` prefix — yes, Spring Session can do it. Verified from the serializer source.**
`DefaultCookieSerializer#writeCookieValue` builds the `Set-Cookie` header by hand and appends, in
order, `Max-Age`, `Expires`, `Domain`, `Path`, `Secure`, `HttpOnly`, `SameSite`, `Partitioned`. The
three facts that matter:

1. **The cookie *name* is not validated.** `setCookieName` only rejects `null`; the character
   validation in the class applies to the cookie *value*, *domain* and *path*, never the name. So
   `__Host-SESSION` is accepted.
2. **`Domain` is omitted entirely** unless `domainName` or `domainNamePattern` is set — the
   serializer appends nothing when `getDomainName(request)` returns null. That satisfies the
   "no Domain attribute" requirement by default, as long as we never set
   `server.servlet.session.cookie.domain`.
3. **`Path` and `Secure` are ours to set**, and Path defaults to the context path (or `/` when the
   context path is empty), so set it explicitly to `/`.

Because Spring Session writes the header itself rather than going through
`SessionCookieConfig`/Tomcat, the container's cookie-name rules are not in the path at all. This is
the crux of the answer and it is why the prefix is feasible here but would be dicier with
container-managed sessions.

**The prefix forces a per-profile cookie *name*.** `__Host-` requires Secure, and a browser will
simply refuse a `__Host-` cookie sent over plain HTTP — so you cannot use the same name in local
HTTP dev. Split it:

`application.properties` (shared):
```properties
spring.session.timeout=15m
server.servlet.session.cookie.name=SESSION
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=strict
server.servlet.session.cookie.path=/
# deliberately no server.servlet.session.cookie.domain — required for __Host-
```

`application-dev.properties`:
```properties
server.servlet.session.cookie.secure=false
```

`application-prod.properties`:
```properties
server.servlet.session.cookie.name=__Host-SESSION
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.max-age=8h
```

**Make "prod cannot ship with Secure off" a hard failure, not a convention.** Properties alone
cannot enforce that — a bad override silently wins. Add a `@Profile("prod")` `@Bean`
`InitializingBean` (or an `ApplicationRunner`) that reads the bound `ServerProperties` and throws on
startup unless `secure == Boolean.TRUE`, `sameSite == STRICT`, `httpOnly == Boolean.TRUE`,
`domain == null`, `path.equals("/")` and `name.startsWith("__Host-")`. Cheap, and it converts a
silent security regression into a failed deploy. Pair it with an integration test asserting the raw
`Set-Cookie` header.

**There is no separate CSRF cookie.** The ticket's item 6 mentions cookie attributes "for both the
session cookie and the CSRF cookie" — with `HttpSessionCsrfTokenRepository` there is no CSRF cookie
at all. That part of the requirement is satisfied by not existing, and the `[Enforced Constraint]`
is better tested by asserting *no* `XSRF-TOKEN` cookie is ever emitted.

---

### 8. Security headers

All from the [headers chapter](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html).
Spring Security applies a default set including cache control, content-type options, HSTS and frame
options; CSP and Referrer-Policy are **not** defaulted because no sensible default exists. Explicit
config (preferred so the audit trail is in the code):

```java
.headers((headers) -> headers
    .httpStrictTransportSecurity((hsts) -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000)
    )
    .contentTypeOptions(Customizer.withDefaults())          // X-Content-Type-Options: nosniff
    .frameOptions((frame) -> frame.deny())                  // X-Frame-Options: DENY
    .contentSecurityPolicy((csp) -> csp
        .policyDirectives("default-src 'self'")
    )
    .referrerPolicy((referrer) -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
)
```

Notes:

- `.frameOptions(frame -> frame.deny())` — the docs only *show* `sameOrigin()`, because DENY is the
  default. I am recommending the explicit `deny()` call; **confirm the method name compiles** (the
  `FrameOptionsConfig` API is not spelled out on that page). Low risk, but it is a name I have not
  seen in the 7.1 docs verbatim, so it goes on the compile-check list.
- `httpStrictTransportSecurity` is emitted on secure requests only, which is why local HTTP dev is
  unaffected. Do not add `preload(true)` without a deliberate decision — preload is effectively
  irreversible for the domain.
- CSP `default-src 'self'` will block inline scripts and styles. Budget for that in the frontend
  ticket rather than discovering it at integration time.
- `.headers(headers -> headers.disable())` and `.defaultsDisabled()` exist; neither should appear in
  our codebase.
- Also still present but **not** to be used: `httpPublicKeyPinning` (HPKP is dead in browsers) and
  `xssProtection` (X-XSS-Protection is obsolete; Spring Security's default disables the auditor,
  which is the right value).

---

### 9. CORS with an explicit allowlist and `allowCredentials=true`

From the [CORS chapter](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html).

**Ordering is handled for us, with a condition.** The docs state CORS must be processed before
Spring Security, because a preflight request carries no cookies and would otherwise be rejected as
unauthenticated. Spring Security integrates `CorsFilter` into the chain for you — but only
**"if a `UrlBasedCorsConfigurationSource` instance is present"**. So declare the bean with that
exact type, not the `CorsConfigurationSource` interface:

```java
@Bean
UrlBasedCorsConfigurationSource corsConfigurationSource(
        @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);                       // explicit allowlist, no "*"
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(1800L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

plus `.cors(Customizer.withDefaults())` in the chain.

Three things to get right:

- **`allowCredentials=true` is incompatible with a `*` origin** — Spring Framework's
  `CorsConfiguration` rejects the combination, and `setAllowedOriginPatterns` is the escape hatch
  when you need wildcards *with* credentials. Since we want an explicit allowlist anyway, use
  `setAllowedOrigins`. (Boot's own actuator CORS property docs state the same rule:
  with credentials allowed, `*` cannot be used and origin patterns should be configured instead —
  see the [application properties appendix](https://docs.spring.io/spring-boot/appendix/application-properties/index.html).)
- **`allowedHeaders` must include our CSRF header**, or the preflight fails and every unsafe request
  dies before it reaches the app.
- **If more than one `CorsConfigurationSource` bean exists, Spring Security stops auto-configuring
  CORS** (it cannot pick). Then you pass it per-chain via `.cors(cors -> cors.configurationSource(...))`.

New in the 7.x line: `PreFlightRequestHandler` / `PreFlightRequestFilter`. If a
`PreFlightRequestHandler` is selected for a chain, Spring Security registers `PreFlightRequestFilter`
before `CorsFilter`. Configuring both `configurationSource` and `preFlightRequestHandler` on the
same `CorsConfigurer` is a **startup error**. We use `configurationSource` only.

Reminder from the docs: disabling `.cors()` does not remove browser CORS enforcement, it removes our
ability to serve a cross-origin frontend. If the SPA is same-origin, the honest answer is to not
enable CORS at all — revisit against the frontend-architecture ticket.

---

### 10. Method security

From the [method security chapter](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html).
`@EnableMethodSecurity` on a `@Configuration` class; `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`,
`@PostFilter` are enabled by default. `@Secured` and JSR-250 need
`@EnableMethodSecurity(securedEnabled = true)` / `(jsr250Enabled = true)` respectively — leave both
off.

```java
@Service
public class AdminUserService {

    @PreAuthorize("hasRole('ADMIN')")
    public void lockAccount(String username) { ... }
}
```

Points that affect our design:

- Denial throws `AccessDeniedException`, which `ExceptionTranslationFilter` turns into **403** for
  HTTP requests, and publishes an `AuthorizationDeniedEvent`. Outside an HTTP request you handle it
  yourself. Our error envelope needs a 403 shape distinct from 401.
- **The same annotation cannot be repeated on a method.** Combine with SpEL boolean logic or
  delegate to a bean.
- The docs advise **against `@PostAuthorize` on methods that write**, and note that if you must
  combine with `@Transactional` you should ensure `@EnableTransactionManagement` is processed before
  `@EnableMethodSecurity`. Relevant to the admin module.
- The docs recommend granting authorities over complex SpEL, using `RoleHierarchy` — likely a better
  fit for the admin role model than `hasRole('X') || hasRole('Y')` expressions.
- Meta-annotations are supported (e.g. a custom `@RequireAdmin`), which is the cleanest way to keep
  role strings out of a hundred call sites.

---

### 11. Breaking changes from Boot 3.x that bite this app

From the [Boot 4.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide),
[4.0 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes) and
[4.1 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes).

**Build / dependency shape (this is the big one):**

- Boot 4 is **modularised**. Modules are `spring-boot-<tech>`, starters are
  `spring-boot-starter-<tech>`, and **each starter has a `-test` companion**. Features that
  previously rode along on a third-party jar now need their own starter.
- `spring-boot-starter-web` is **deprecated** in favour of **`spring-boot-starter-webmvc`**.
- `spring-boot-starter-security` + **`spring-boot-starter-security-test`** — and note the guide
  states `@WithMockUser` / `@WithUserDetails` now require the test starter to work properly. Miss it
  and your security tests pass for the wrong reason.
- Flyway needs `spring-boot-starter-flyway`.
- `spring-boot-starter-session-jdbc` (+ `-test`) as covered in item 3.
- `spring-boot-starter-classic` / `spring-boot-starter-test-classic` exist as a migration crutch.
  We are greenfield — do not use them.

**Servlet container:**

- Servlet **6.1** baseline. **Undertow support is removed entirely** because it is not 6.1
  compatible. Tomcat 11 via `spring-boot-starter-tomcat`. The guide advises against deploying Boot 4
  to a non-6.1 container.
- `server.forward-headers-strategy` no longer has an effect for war deployment to an external
  container. We deploy the embedded server, so this is informational — but it is worth being
  deliberate about forwarded-header handling given the `useSecureCookie` behaviour in item 7.
- `PathRequest#toStaticResources().atCommonLocations()` now includes `/fonts/**`.

**Jackson 3 — affects our JSON login and any session serialization:**

- Jackson 3 is the default; group/package `com.fasterxml.jackson` becomes **`tools.jackson`**
  (except `jackson-annotations`). Boot auto-configures a **`JsonMapper`** (and `XmlMapper`);
  defining an `ObjectMapper` bean is **no longer sufficient** to replace it.
- `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`; `@JsonComponent` →
  `@JacksonComponent`; `@JsonMixin` → `@JacksonMixin`.
- `spring.jackson.read.*` / `write.*` moved under `spring.jackson.json.read` / `.json.write`;
  `spring.jackson.parser.*` largely replaced.
- **All classpath modules are now auto-registered** with mapper instances (Boot 3 registered only
  well-known ones); `spring.jackson.find-and-add-modules=false` disables it.
- Escape hatches exist (`spring.jackson.use-jackson2-defaults`, the deprecated `spring-boot-jackson2`
  module). We should not need them, but the mismatch with Spring Session's Jackson-2-era JSON
  attribute example (item 2) is exactly why we keep JDK serialization.
- **Spring Security 7.0 itself is on `tools.jackson:jackson-bom` 3.x**
  ([7.0.0 release notes](https://github.com/spring-projects/spring-security/releases/tag/7.0.0)),
  so the security-side Jackson modules are Jackson 3 aligned.

**Testing:**

- `@MockBean` / `@SpyBean` **removed** → `@MockitoBean` / `@MockitoSpyBean`, and the new annotations
  are **not allowed in `@Configuration` classes`** — use `@MockitoBean(types = {...})` on the test
  class or a custom composed annotation.
- `@SpringBootTest` **no longer provides MockMvc** — add `@AutoConfigureMockMvc`. Likewise
  `TestRestTemplate` / `WebClient` need `@AutoConfigureTestRestTemplate` /
  `@AutoConfigureRestTestClient`. Consider `RestTestClient` instead.
- `MockitoTestExecutionListener` removed — plain `@Mock`/`@Captor` now need Mockito's own
  `MockitoExtension`.

**Other:**

- **JSpecify** nullability annotations throughout Spring Boot/Framework/Security/Session (visible in
  every source file I read). If we run a null checker, expect new findings.
- `spring.session.redis` → `spring.session.data.redis` (JDBC properties unchanged — good for us).
- Spring Session Hazelcast and MongoDB support removed from Boot.
- Spring Authorization Server has moved **into** Spring Security 7; override its version via
  `spring-security.version`.
- Logback file charset now defaults to UTF-8.
- `spring-boot-properties-migrator` (runtime scope, temporarily) will diagnose renamed properties —
  worth a single run even greenfield, to catch properties copied from Boot 3 material.
- Boot 4.1 specifically: everything deprecated in 4.0 is **removed**; jOOQ 3.20 needs Java 21+;
  `spring.datasource.connection-fetch=lazy` is new; `spring.http.clients.cookie-handling` and an
  `InetAddressFilter` for SSRF hardening are new (potentially interesting for us later).

**Security-side breaking changes** are in item 1 — the lambda-DSL requirement, matcher change,
`PortResolver` removal, `requiresChannel` → `redirectToHttps`, relative login redirects, the
`DaoAuthenticationProvider` constructor, and the new factor authority.

**Against "Inventory the prescribed recipes" (issue 04):** any recipe that uses
`spring-boot-starter-web`, `WebSecurityConfigurerAdapter`, `antMatchers`/`mvcMatchers`, non-lambda
DSL chaining with `.and()`, `@MockBean`, `new DaoAuthenticationProvider()`, or a
`com.fasterxml.jackson` import is Boot 3 material and must be rewritten. That is a mechanical
cross-check worth doing explicitly before the build phase.

---

### 12. BCrypt: >72-byte behaviour, and `DaoAuthenticationProvider` timing mitigation

**The two advisories, as published:**

- [CVE-2025-22228](https://spring.io/security/cve-2025-22228) — `BCryptPasswordEncoder.matches`
  incorrectly returned true for passwords longer than 72 characters when the first 72 matched.
  Fixed in 6.4.4 / 6.3.8.
- [CVE-2025-22234](https://spring.io/security/cve-2025-22234) — the CVE-2025-22228 fix
  "inadvertently broke the timing attack mitigation implemented in DaoAuthenticationProvider".
  Fixed in 6.4.5 / 6.3.9.

Both are long fixed relative to 7.1.1. The useful question is what the code does *now*, because that
determines our error contract. Answers below are read from the `main`-branch source.

**Encoding a >72-byte password: it THROWS.** Not truncation, not rejection-as-false.
`BCryptPasswordEncoder#encodeNonNullPassword` calls `BCrypt.hashpw(rawPassword, salt)`
([BCryptPasswordEncoder](https://raw.githubusercontent.com/spring-projects/spring-security/main/crypto/src/main/java/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoder.java)),
and `BCrypt.hashpw` guards on the **UTF-8 byte length**, throwing
`IllegalArgumentException("password cannot be more than 72 bytes")`
([BCrypt.java](https://raw.githubusercontent.com/spring-projects/spring-security/main/crypto/src/main/java/org/springframework/security/crypto/bcrypt/BCrypt.java)).

**Verifying a >72-byte password: no guard, full work, and it can still match.** The guard is
deliberately scoped — the source comment reads
"Enforce max length for new passwords only" — and the check path runs with an internal
`for_check` flag that skips it. `BCryptPasswordEncoder#matchesNonNull` → `BCrypt.checkpw` →
`hashpwforcheck(...)`, which performs the full bcrypt hash and then a constant-time comparison via
`MessageDigest.isEqual` (same source files). **This asymmetry *is* the CVE-2025-22234 fix**: if
`matches` short-circuited on long input, an attacker could distinguish "unknown user" from "known
user, wrong password" by timing. Because bcrypt itself only consumes the first 72 bytes, a
>72-byte candidate will still match a stored hash derived from the same first 72 bytes — the safety
now comes from the fact that `encode` refuses to *create* such a hash.

**What this means for our error contract:**

| Flow | Input | Behaviour | Our contract |
|---|---|---|---|
| Registration / password change | > 72 UTF-8 bytes | `IllegalArgumentException` from the crypto layer | **Never let it reach the encoder.** Validate ≤ 72 **bytes** (not characters) at the DTO boundary and return the standard 400 validation envelope. A raw `IllegalArgumentException` would surface as a 500. |
| Login | > 72 UTF-8 bytes | full bcrypt work, then `false` | Standard generic "Bad credentials". **Do not add a length check on the login path** — an early reject reintroduces exactly the timing oracle CVE-2025-22234 fixed. |

The byte-vs-character distinction matters: `hashpw` measures `password.getBytes(UTF_8).length`, so a
54-character string of 2-byte characters exceeds the limit. Validate bytes.

Also note two related `main`-branch facts. `BCryptPasswordEncoder` now extends
`AbstractValidatingPasswordEncoder`, whose `matches` is `final` and returns **false when either
argument is null or an empty string**
([javadoc](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/crypto/password/AbstractValidatingPasswordEncoder.html)) —
so an empty submitted password never reaches bcrypt, and an empty *stored* hash cannot match
anything. And `matchesNonNull` first tests the stored value against a BCrypt regex, logging a
warning and returning false if it does not look like BCrypt — useful for spotting a bad migration,
but it does mean a corrupted hash produces a fast false. That is a per-account condition, not a
per-username oracle, so it does not undermine the mitigation; still worth an alert on that log line.

**`DaoAuthenticationProvider` timing mitigation for unknown users — current behaviour, verified.**
From [DaoAuthenticationProvider](https://raw.githubusercontent.com/spring-projects/spring-security/main/core/src/main/java/org/springframework/security/authentication/dao/DaoAuthenticationProvider.java):

1. `retrieveUser` calls `prepareTimingAttackProtection()` **before** `loadUserByUsername`, which
   lazily encodes the constant `USER_NOT_FOUND_PASSWORD` (the literal `"userNotFoundPassword"`) with
   the **configured** encoder and caches the result in a volatile field. Setting a new encoder via
   `setPasswordEncoder` resets the cache.
2. If `UsernameNotFoundException` is thrown, `mitigateAgainstTimingAttack(authentication)` runs
   `passwordEncoder.matches(presentedPassword, userNotFoundEncodedPassword)` — a real bcrypt
   verification, result discarded — and then rethrows.
3. `AbstractUserDetailsAuthenticationProvider#authenticate` catches it and, because
   `hideUserNotFoundExceptions` defaults to **`true`**, throws
   `BadCredentialsException("Bad credentials")` instead
   ([source](https://raw.githubusercontent.com/spring-projects/spring-security/main/core/src/main/java/org/springframework/security/authentication/dao/AbstractUserDetailsAuthenticationProvider.java)).
   The source comment on the dummy password explains why a constant is needed at all: some encoders
   short-circuit on a malformed stored password.

So the mitigation is **on by default and requires no configuration**, and the dummy plaintext is
comfortably under 72 bytes so the encode step cannot throw. Four contract implications:

- **Unknown user and wrong password are indistinguishable** — same exception, same message, same
  approximate cost. Our login response must preserve that: one generic failure envelope, identical
  status, no "user not found" branch, and identical response for both. Cross-check against the
  enumeration contract in issue 06.
- **Do not set `setHideUserNotFoundExceptions(false)`.** The javadoc itself calls it less secure.
- **Do not put a lockout/rate-limit branch that short-circuits before authentication on username
  existence** — that reintroduces enumeration at the application layer even though the crypto layer
  is clean. Cross-check against issue 09.
- If we ever wrap `PasswordEncoder` (e.g. for metrics or a pepper), the wrapper must **not** fast-path
  on unparseable input, or it breaks the mitigation the same way CVE-2025-22228's fix did.

`DelegatingPasswordEncoder` is the default when no encoder is set
(`PasswordEncoderFactories::createDelegatingPasswordEncoder`, per the source). We will set
`BCryptPasswordEncoder` explicitly; whether to keep the `{bcrypt}` prefix format for future
algorithm migration belongs to issue 07.

---

### Open risks for the build phase — verify by compiling, do not assume

Ranked by "how badly does a wrong guess hurt".

1. **`maximumSessions(1)` actually firing with `SpringSessionBackedSessionRegistry`.** Depends on the
   `PRINCIPAL_NAME` index being populated. Fails **silently** — the app works, the control does not.
   *Integration test: log in twice as the same user, assert the first session is rejected.*
2. **The JSON-login filter (Route C, item 6).** Assembled from verified primitives but not a
   documented recipe. *Compile check plus tests for: session id rotation on login, concurrency
   control firing, malformed body → standard envelope not 500, CSRF still enforced on `POST /login`.*
3. **The absolute-lifetime mechanism (item 5b).** Entirely ours; the library provides nothing.
   *Compile check plus an integration test with a 2-second lifetime. Explicitly assert that the clock
   starts at authentication, not at first session creation.*
4. **`__Host-` prefixed cookie end-to-end.** The serializer will emit it (verified from source), but
   real browser acceptance depends on Secure + `Path=/` + absent Domain all being right
   simultaneously. *Assert the raw `Set-Cookie` header in an integration test, and add the
   fail-fast prod startup check.*
5. **`.frameOptions(frame -> frame.deny())` method name.** Not shown verbatim in the 7.1 docs.
   *Compile check.* Same for `ReferrerPolicy.SAME_ORIGIN` as an import path.
6. **Composite strategy class names and constructors** used in item 6 —
   `CompositeSessionAuthenticationStrategy(List<...>)`, `ChangeSessionIdAuthenticationStrategy()`,
   `RegisterSessionAuthenticationStrategy(SessionRegistry)`,
   `ConcurrentSessionControlAuthenticationStrategy(SessionRegistry)`. All confirmed present in
   `SessionManagementConfigurer`'s imports and usage, but *constructor visibility from our package
   needs a compile check.*
7. **The new `FactorGrantedAuthority` on successful authentication.** Source-derived. *Assert the
   actual authority collection in a test before writing any `@PreAuthorize` that enumerates
   authorities, and before asserting exact authority sets.*
8. **Exact Security 7 removal list.** Incomplete, as stated. The `migration-7` reference pages 404 on
   the 7.x docs and the 6.5 copies are the best surviving source. *Mitigation: compile early against
   7.1.1 and treat the compiler as the authority; budget time for removals I have not listed.*
9. **CSRF token serialization into `SPRING_SESSION_ATTRIBUTES`.** Fine under JDK serialization.
   *Verify a login + CSRF round trip survives a restart of the app with the DB retained.*
10. **Version drift.** I verified against the current published docs and `main`. `main` runs ahead of
    7.1.1 / 4.1.1. Anything marked source-derived should be re-confirmed against the exact pinned
    versions at build time.

### Sources

Spring Security 7.1 reference:
[CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) ·
[Session management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html) ·
[Headers](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html) ·
[CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html) ·
[Method security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html) ·
[Form login](https://docs.spring.io/spring-security/reference/7.1/servlet/authentication/passwords/form.html)

Spring Security "Preparing for 7.0" (6.5 line):
[Configuration migrations](https://docs.spring.io/spring-security/reference/6.5/migration-7/configuration.html) ·
[Web migrations](https://docs.spring.io/spring-security/reference/6.5/migration-7/web.html)

Spring Security javadoc (7.x API):
[BCryptPasswordEncoder](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoder.html) ·
[AbstractValidatingPasswordEncoder](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/crypto/password/AbstractValidatingPasswordEncoder.html) ·
[AuthenticationFilter](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/web/authentication/AuthenticationFilter.html) ·
[AbstractAuthenticationProcessingFilter](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/web/authentication/AbstractAuthenticationProcessingFilter.html) ·
[FormLoginConfigurer](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/config/annotation/web/configurers/FormLoginConfigurer.html) ·
[CsrfConfigurer](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/config/annotation/web/configurers/CsrfConfigurer.html)

Advisories: [CVE-2025-22228](https://spring.io/security/cve-2025-22228) ·
[CVE-2025-22234](https://spring.io/security/cve-2025-22234)

Spring Session reference:
[JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html) ·
[Spring Security integration](https://docs.spring.io/spring-session/reference/spring-security.html) ·
[API / CookieSerializer](https://docs.spring.io/spring-session/reference/api.html)

Spring Boot:
[Spring Session](https://docs.spring.io/spring-boot/reference/web/spring-session.html) ·
[dependency versions](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html) ·
[application properties appendix](https://docs.spring.io/spring-boot/appendix/application-properties/index.html) ·
[4.0 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes) ·
[4.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) ·
[4.1 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)

Source read on `main` (spring-projects):
[BCryptPasswordEncoder](https://raw.githubusercontent.com/spring-projects/spring-security/main/crypto/src/main/java/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoder.java) ·
[BCrypt](https://raw.githubusercontent.com/spring-projects/spring-security/main/crypto/src/main/java/org/springframework/security/crypto/bcrypt/BCrypt.java) ·
[DaoAuthenticationProvider](https://raw.githubusercontent.com/spring-projects/spring-security/main/core/src/main/java/org/springframework/security/authentication/dao/DaoAuthenticationProvider.java) ·
[AbstractUserDetailsAuthenticationProvider](https://raw.githubusercontent.com/spring-projects/spring-security/main/core/src/main/java/org/springframework/security/authentication/dao/AbstractUserDetailsAuthenticationProvider.java) ·
[UsernamePasswordAuthenticationFilter](https://raw.githubusercontent.com/spring-projects/spring-security/main/web/src/main/java/org/springframework/security/web/authentication/UsernamePasswordAuthenticationFilter.java) ·
[AbstractAuthenticationProcessingFilter](https://raw.githubusercontent.com/spring-projects/spring-security/main/web/src/main/java/org/springframework/security/web/authentication/AbstractAuthenticationProcessingFilter.java) ·
[SessionManagementConfigurer](https://raw.githubusercontent.com/spring-projects/spring-security/main/config/src/main/java/org/springframework/security/config/annotation/web/configurers/SessionManagementConfigurer.java) ·
[ConcurrentSessionControlAuthenticationStrategy](https://raw.githubusercontent.com/spring-projects/spring-security/main/web/src/main/java/org/springframework/security/web/authentication/session/ConcurrentSessionControlAuthenticationStrategy.java) ·
[CacheControlHeadersWriter](https://raw.githubusercontent.com/spring-projects/spring-security/main/web/src/main/java/org/springframework/security/web/header/writers/CacheControlHeadersWriter.java) ·
[Session](https://raw.githubusercontent.com/spring-projects/spring-session/main/spring-session-core/src/main/java/org/springframework/session/Session.java) ·
[DefaultCookieSerializer](https://raw.githubusercontent.com/spring-projects/spring-session/main/spring-session-core/src/main/java/org/springframework/session/web/http/DefaultCookieSerializer.java) ·
[SpringSessionBackedSessionRegistry](https://raw.githubusercontent.com/spring-projects/spring-session/main/spring-session-core/src/main/java/org/springframework/session/security/SpringSessionBackedSessionRegistry.java) ·
[SpringSessionBackedSessionInformation](https://raw.githubusercontent.com/spring-projects/spring-session/main/spring-session-core/src/main/java/org/springframework/session/security/SpringSessionBackedSessionInformation.java) ·
[schema-postgresql.sql](https://raw.githubusercontent.com/spring-projects/spring-session/main/spring-session-jdbc/src/main/resources/org/springframework/session/jdbc/schema-postgresql.sql) ·
[SessionAutoConfiguration](https://raw.githubusercontent.com/spring-projects/spring-boot/main/module/spring-boot-session/src/main/java/org/springframework/boot/session/autoconfigure/SessionAutoConfiguration.java) ·
[SessionProperties](https://raw.githubusercontent.com/spring-projects/spring-boot/main/module/spring-boot-session/src/main/java/org/springframework/boot/session/autoconfigure/SessionProperties.java) ·
[DefaultCookieSerializerCustomizer](https://raw.githubusercontent.com/spring-projects/spring-boot/main/module/spring-boot-session/src/main/java/org/springframework/boot/session/autoconfigure/DefaultCookieSerializerCustomizer.java) ·
[JdbcSessionProperties](https://raw.githubusercontent.com/spring-projects/spring-boot/main/module/spring-boot-session-jdbc/src/main/java/org/springframework/boot/session/jdbc/autoconfigure/JdbcSessionProperties.java) ·
[DatabaseInitializationProperties](https://raw.githubusercontent.com/spring-projects/spring-boot/main/module/spring-boot-jdbc/src/main/java/org/springframework/boot/jdbc/init/DatabaseInitializationProperties.java) ·
[Cookie](https://raw.githubusercontent.com/spring-projects/spring-boot/main/module/spring-boot-web-server/src/main/java/org/springframework/boot/web/server/Cookie.java)

Content from the sources above was rephrased and summarised for compliance with licensing
restrictions; verbatim quotation is kept minimal.
