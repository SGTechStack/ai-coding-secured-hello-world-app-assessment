# Findings: Session store & per-user invalidation (ticket 02)

Scope: current stable Spring Boot / Spring Session / Spring Security line on Java 21. All claims
sourced from primary docs (docs.spring.io, github.com/spring-projects).

## Version pins (as of Sept 2026)

- **Spring Boot 4.1.x** is the latest stable line (4.1.1; 4.0.8 and 3.5.16 also maintained;
  4.2.0-M1 is preview). Boot 4.0 GA'd 2025-11-20 and requires Java 17+ — Java 21 is fully
  supported.
  Sources: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes ,
  https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide ,
  https://docs.spring.io/spring-boot/reference/web/spring-session.html (version list in footer)
- Boot 4 manages **Spring Security 7.x** and **Spring Session 4.x** (4.1.1 latest stable;
  Spring Session requires Java 17+, Servlet 3.1+).
  Sources: https://github.com/spring-projects/spring-session/releases/tag/4.0.0 ,
  https://docs.spring.io/spring-session/reference/4.0/index.html ,
  https://docs.spring.io/spring-session/reference/spring-security.html (version list)
- Spring Session JDBC dependency under Boot 4: `spring-boot-starter-session-jdbc`
  (or `org.springframework.session:spring-session-jdbc` directly; Boot auto-config then replaces
  `@EnableJdbcHttpSession`).
  Sources: https://docs.spring.io/spring-boot/reference/web/spring-session.html ,
  https://docs.spring.io/spring-session/reference/configuration/jdbc.html

## 1. Which store — and can the in-memory repo enumerate a user's sessions?

### In-memory `MapSessionRepository` — does NOT support the principal index

- On the current line (verified in the 3.5.x and 4.x javadoc), `MapSessionRepository` implements
  only `SessionRepository<MapSession>` — it does **not** implement
  `FindByIndexNameSessionRepository`, so there is no `findByIndexNameAndIndexValue` /
  `findByPrincipalName`. You cannot enumerate or bulk-delete one user's sessions with it.
  Sources: https://docs.spring.io/spring-session/docs/3.5.x/api/org/springframework/session/MapSessionRepository.html ,
  https://docs.spring.io/spring-session/reference/api/java/org/springframework/session/FindByIndexNameSessionRepository.html
- It also does not fire `SessionDeletedEvent`/`SessionExpiredEvent`, and the backing map itself is
  responsible for purging expired sessions (same javadoc).
- Spring Boot auto-configures only Redis and JDBC servlet stores — there is no Boot-managed
  in-memory store; using `MapSessionRepository` means hand-wiring `@EnableSpringHttpSession` and
  still getting no per-user lookup.
  Source: https://docs.spring.io/spring-boot/reference/web/spring-session.html

### Spring Session JDBC — RECOMMENDED

- `JdbcIndexedSessionRepository` implements
  `FindByIndexNameSessionRepository<JdbcSession>`: `findByIndexNameAndIndexValue` /
  `findByPrincipalName` return `Map<sessionId, Session>`, and `deleteById` removes a session.
  The `SPRING_SESSION` table carries a `PRINCIPAL_NAME` column with index `SPRING_SESSION_IX3`.
  Sources: https://docs.spring.io/spring-session/docs/4.0.x/api/org/springframework/session/jdbc/JdbcIndexedSessionRepository.html ,
  https://docs.spring.io/spring-session/reference/configuration/jdbc.html
- With Spring Security present, the principal name is indexed **automatically** when the security
  context is saved into the session — no manual index population needed. (For non-Spring-Security
  auth you'd set `FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME` yourself.)
  Source: https://docs.spring.io/spring-session/reference/guides/boot-findbyusername.html —
  that guide's motivating scenario is exactly ours: "invalidate the user's session that might
  have been compromised."
- Extra machinery is small: two tables (`SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`), and
  `spring.session.jdbc.initialize-schema=embedded` is the **default** — Boot runs the packaged
  `schema-@@platform@@.sql` (H2 included) automatically on the embedded dev datasource; zero
  manual DDL. Portable to Postgres/MySQL later (vendor scripts ship in the jar), matching the
  PRD's portability goal.
  Sources: https://docs.spring.io/spring-session/reference/guides/boot-jdbc.html ,
  https://docs.spring.io/spring-session/reference/configuration/jdbc.html
- Bonus: sessions survive app restart, and if concurrent-session limits are wanted later,
  `SpringSessionBackedSessionRegistry` (a `SessionRegistry` backed by the same
  `FindByIndexNameSessionRepository`) works cluster-wide, unlike the default in-memory registry.
  Source: https://docs.spring.io/spring-session/reference/spring-security.html

### Spring Security `SessionRegistry` — viable but a weaker fit here

- `SessionRegistryImpl` + `sessionManagement().maximumSessions(...)` + a
  `HttpSessionEventPublisher` bean gives `getAllSessions(principal, false)` → `expireNow()`.
  It is in-memory and per-JVM.
  Source: https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html
- Two catches on the current line: (a) since Spring Security 6, `SessionManagementFilter` is not
  installed by default — **the authentication mechanism itself must invoke the
  `SessionAuthenticationStrategy`**, so a controller-driven login must explicitly call the
  strategy (`onAuthentication`) or the registry never learns about the login; and (b) the default
  registry keys principals in an in-memory map — a custom `UserDetails` must override
  `equals()`/`hashCode()`.
  Source: same page (sections "Configuring Concurrent Session Control", "Moving Away From
  `SessionManagementFilter`", and the equals/hashCode caution).
- Verdict: it works for a single-instance demo but adds wiring and a restart-fragile registry;
  `findByPrincipalName` + `deleteById` on JDBC is fewer moving parts and survives restarts.

### Custom userId→sessionIds registry — unnecessary

- Would re-implement what `FindByIndexNameSessionRepository` already provides; only worth it if
  staying on plain container sessions and avoiding both Spring Session and `SessionRegistry`.

## 2. Logout / cookie-replay semantics

- Spring Session's `SessionRepositoryFilter` replaces `HttpSession` with a store-backed
  implementation; `invalidate()` deletes the record server-side (`deleteById`) and the cookie
  serializer writes an expired cookie on commit.
  Source: https://docs.spring.io/spring-session/reference/http-session.html ,
  https://docs.spring.io/spring-session/reference/configuration/jdbc.html
- A replayed cookie then presents an unknown session id → `findById` returns null → no
  `SecurityContext` is restored → the request is anonymous → protected endpoints return **401**.
  Same net result for container sessions (invalidated id = no context), but JDBC additionally
  guarantees the record is gone even across restarts.
- Caveat from Spring Security docs: "the session cookie is not cleared when you invalidate the
  session and will be resubmitted even if the user has logged out" — so explicitly clear it via
  `logout().deleteCookies(...)` (Spring Session also expires the cookie on invalidate, but belt
  and suspenders costs nothing).
  Source: https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html
- For manual/controller logout, `SecurityContextLogoutHandler.logout(request, response,
  authentication)` clears the context; combined with `session.invalidate()` this satisfies
  Story 4's replay-rejection AC.
  Source: same page ("Properly Clearing an Authentication").

## 3. Session fixation under REST (controller-driven) login

- Options are `changeSessionId` (default on Servlet 3.1+ containers), `newSession`, and
  `migrateSession`.
  Source: https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html
- **Key gotcha:** since Spring Security 6 the `SessionManagementFilter` is not in the chain by
  default — "authentication mechanisms themselves must invoke the
  `SessionAuthenticationStrategy`". The `sessionFixation()` DSL therefore does **nothing** for a
  login implemented inside a `@RestController`; the controller must do it explicitly.
  Source: same page ("Moving Away From `SessionManagementFilter`").
- Required wiring in the login controller (matches the documented manual-auth recipe):
  1. `authenticationManager.authenticate(token)`
  2. `request.changeSessionId()` — the Servlet 3.1+ default strategy's primitive; safe under
     Spring Session (the request wrapper supports it)
  3. store the `Authentication` in a fresh `SecurityContext`, set it on the
     `SecurityContextHolderStrategy`, and persist with
     `securityContextRepository.saveContext(context, request, response)`
     (`HttpSessionSecurityContextRepository`) — explicit save is required since Spring Security 6.
  Source: same page ("Storing the `Authentication` manually", "Understanding Require Explicit
  Save").

## 4. Cookie attributes for the chosen store

- With Spring Session, the session cookie is written by `CookieHttpSessionIdResolver` +
  `DefaultCookieSerializer`, **not** by the servlet container — raw `SessionCookieConfig` does
  not apply.
  Source: https://docs.spring.io/spring-session/reference/guides/java-custom-cookie.html
- However, under Spring Boot, `SessionAutoConfiguration` creates a `DefaultCookieSerializer`
  bean mapped from `server.servlet.session.cookie.*` — name, domain, path, `httpOnly`, `secure`,
  `maxAge`, `sameSite`, `partitioned` — so the familiar properties DO work with Spring Session
  on Boot. Caveat: declaring your own `CookieSerializer`/`HttpSessionIdResolver` bean makes this
  auto-config back off (`DefaultCookieSerializerCondition`), and you must then set every
  attribute yourself.
  Source: https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-session/src/main/java/org/springframework/boot/session/autoconfigure/SessionAutoConfiguration.java
- `DefaultCookieSerializer` defaults: `HttpOnly` = **true**; `SameSite` = **Lax**; `Secure` =
  `request.isSecure()` at creation time (prod HTTPS → Secure automatically).
  Source: https://docs.spring.io/spring-session/reference/api/java/org/springframework/session/web/http/DefaultCookieSerializer.html
- SameSite note for this app: `localhost:3000` → `localhost:8080` is *same-site* (same schemeful
  site), so `Lax` permits credentialed `fetch`. Only genuinely cross-site deployments would need
  `SameSite=None; Secure`.

## Recommendation

**Spring Session JDBC over H2** (`spring-boot-starter-session-jdbc`). It is the only in-scope
option where "invalidate all sessions for user X" is a supported, indexed query
(`findByPrincipalName` + `deleteById`); schema is auto-created on the dev H2 datasource; and it
matches the PRD's production-grade/portable-storage posture. `MapSessionRepository` cannot
enumerate per-user sessions on the current line, and `SessionRegistry` adds wiring while staying
memory-only.

### Config sketch

```properties
# application.properties (dev)
spring.session.jdbc.initialize-schema=embedded   # default; creates SPRING_SESSION* on H2
spring.session.timeout=30m
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=lax
server.servlet.session.cookie.secure=false       # dev over HTTP

# application-prod.properties
server.servlet.session.cookie.secure=true        # requires HTTPS (PRD deployment assumption)
```

```java
// Invalidate every session for a user (password-reset-confirm, admin disable/delete)
private final FindByIndexNameSessionRepository<? extends Session> sessions;

void invalidateAllSessions(String username) {
    sessions.findByPrincipalName(username).keySet()
            .forEach(sessions::deleteById);
}

// Login controller essentials (controller-driven auth, Spring Security 7)
Authentication auth = authenticationManager.authenticate(token);
request.changeSessionId();                                    // fixation protection — explicit
SecurityContext context = securityContextHolderStrategy.createEmptyContext();
context.setAuthentication(auth);
securityContextHolderStrategy.setContext(context);
securityContextRepository.saveContext(context, request, response); // persists to session → indexes principal

// Logout (either via DSL):
http.logout(l -> l.logoutUrl("/api/logout")
        .invalidateHttpSession(true).deleteCookies("SESSION")
        .logoutSuccessHandler((req, res, a) -> res.setStatus(HttpServletResponse.SC_OK)));
// or in a controller: new SecurityContextLogoutHandler().logout(req, res, auth);
//   + request.getSession(false).invalidate();
```

Cookie note: the Spring Session cookie name defaults to `SESSION` (not `JSESSIONID`) —
`deleteCookies("SESSION")` must match `server.servlet.session.cookie.name` if overridden.
