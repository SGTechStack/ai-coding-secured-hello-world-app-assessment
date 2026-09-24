# Web Application Security Review — 2026-09-22

|              |                                                                                                          |
|--------------|----------------------------------------------------------------------------------------------------------|
| **Scope**    | Whole repo — `backend/` (all `src/main`), `frontend/src`, `application*.yml`, `logback-spring.xml`, `pom.xml`, `package.json`, `vite.config.ts`, `e2e/` |
| **Stack**    | Spring Boot 4.1.1 / Security 7.1 · Spring Session JDBC over in-memory H2 · React 19 + Vite SPA (two origins: API `:8080`, SPA `:3000`) |
| **Runtime**  | Verified live — app run from freshly compiled `target/classes` (`dev` profile, `:18080`); the `target/*.jar` was the stale acceptance-run build (SPA-hosting glue) and is file-locked by the running PID 28624, so it was left untouched |
| **Follows**  | `hello-auth-post-fix-report.md` (2026-09-16) — all its F-01..F-08 fixes re-verified; new findings continue at F-09 |

## Summary

| Sev       | Count |
|-----------|-------|
| Critical  | 0     |
| High      | 0     |
| Medium    | 0     |
| Low       | 5     |
| Info      | 4     |

**Result: 9 sections — 8 PASS, 1 NOT VERIFIED (§3, deployment-dependent).** No regressions in the previously closed findings.

## Runtime evidence digest

App started: `java -cp target/classes;<deps> HelloAuthApplication --spring.profiles.active=dev --server.port=18080`

```http
# curl -si http://localhost:18080/api/auth/me   (anonymous)
HTTP/1.1 401
Set-Cookie: XSRF-TOKEN=…; Path=/; SameSite=Lax
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Security-Policy: default-src 'none'; frame-ancestors 'none'
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

- No `Server`/`X-Powered-By` emitted; no HSTS over plain HTTP (expected — see §3).
- `POST /api/auth/login` with valid `X-XSRF-TOKEN` → `200` + `Set-Cookie: SESSION=…; Path=/; HttpOnly; SameSite=Lax`; XSRF-TOKEN cleared (rotated) on login.
- Login, unknown user vs. wrong password → byte-identical `401 {"detail":"Invalid username or password.","status":401,"title":"Login failed"}`.
- `POST /api/auth/logout` → `200`; replayed session cookie afterwards → `401` (server-side row deleted).
- IP throttle: after 4 failed logins from one IP, attempt 5+ → `429` — including an attempt with the **correct** password.
- Anonymous-request bucket: 21st `POST /api/auth/register` inside the 10-min window → `429` (budget `anon-max-requests: 20`); the same bucket also gates `password-reset/request` (429 observed).
- CORS preflight: `Origin: http://localhost:3000` → `200` + `Access-Control-Allow-Origin` + `Allow-Credentials: true`; `Origin: https://evil.example.com` → `403`, no `Access-Control-*`.
- USER session → `GET /api/admin/users` → `403`; ADMIN → `200`. Anonymous → `401` everywhere.
- Malformed JSON → `400 {"detail":"Failed to read request"}`; oversized/missing fields → `400 "Invalid request content."` — no internals, no field-level echo.
- `GET /` (bundled SPA in `static/`) → `401`; `/actuator` → `401`; `/actuator/env` → `401`; `/actuator/health` → `200`; `/h2-console/` → `200` under `dev` only.
- Structured audit lines verified live: `login_success`, `login_failure` with `reason: bad_credentials` emitted as one JSON object per event.

## Findings

### F-09 [LOW] `password-reset/confirm` sits outside the anonymous-request throttle
- **Checklist:** §2 Authentication & session management (rate-limiting)
- **Status:** FAIL (minor)
- **Evidence:** `backend/.../passwordreset/PasswordResetController.java:60-65` — `confirm` calls the service directly; compare `request` at `:48-55` which gates on `ipThrottle.isAnonThrottled`. `AuthController.register` (:75-82) has the gate; `confirm` is the only unauthenticated mutation without it.
- **Impact:** limited — each confirm costs a SHA-256 + indexed lookup, BCrypt runs only after a valid token claim, and the 256-bit token space makes guessing infeasible. Inconsistent defense-in-depth, not an exploitable hole.
- **Remediation:** add the same `isAnonThrottled`/`recordAnonRequest` gate to `confirm` for uniformity.

### F-10 [LOW] `log-reset-link: true` is the base-profile default
- **Checklist:** §8 Data protection — sensitive data not logged
- **Status:** FAIL (minor)
- **Evidence:** `application.yml:76` (`log-reset-link: true`) vs. `application-prod.yml:38` (`false`); `EmailService.java:39-48` logs `link-base-url?token=<plaintext>` when on. The token is a live bearer credential.
- **Impact:** any deployment that forgets the `prod` profile — or runs the default profile with log aggregation — writes working reset links to logs.
- **Remediation:** safer polarity — default `false` in `application.yml`, set `true` in `application-dev.yml` where a developer actually reads the console.

### F-11 [LOW] Stale untracked SPA bundle inside `backend/src/main/resources/static/`
- **Checklist:** §5/§6 (deployment hygiene)
- **Status:** FAIL (minor)
- **Evidence:** `backend/src/main/resources/static/index.html` + `assets/index-CM4hm9uE.js` are untracked leftovers of the acceptance run (README §"single-origin bundle" caveat). Verified live: `GET /` → `401`, so the bundle currently serves nothing.
- **Impact:** none today — the committed chain 401s static GETs and the CSP would break the app anyway. Risk is a future hosting change silently shipping a divergent, unbuilt-from-source SPA.
- **Remediation:** delete `backend/src/main/resources/static/` (it's generated output) and keep single-origin hosting an explicit, tested decision.

### F-12 [LOW] Registration 409s enumerate usernames/emails (accepted, carried over)
- **Checklist:** §9 Error handling — user enumeration
- **Status:** FAIL (accepted risk)
- **Evidence:** `UserService.java:34-39` + `ApiExceptionHandler.java:22-37`; verified live — duplicate username → `409 "Username 'alice' is already taken."` while unknown → `201`. The reset flow is enumeration-resistant (`PasswordResetController.java:27-28`); registration is deliberately not (spec-ratified UX trade-off per prior report).
- **Remediation:** if enumeration becomes a concern, return a uniform "check your email"-style flow; otherwise document as accepted.

### F-13 [LOW] In-memory H2 (`sa`/empty password, `create-drop`) in all profiles including `prod`
- **Checklist:** §8 Data protection at rest
- **Status:** FAIL (demo scope)
- **Evidence:** `application.yml:4-11` — `jdbc:h2:mem:helloauth`, `username: sa`, `password: ""`; no datasource override in `application-prod.yml`. H2 console is `dev`-only and no remote listener exists, so there is no network attack surface — the exposure is durability (all users/sessions/tokens lost on restart) and trivial local read via the dev console.
- **Remediation:** before any non-demo deployment, bind a real datasource (env-driven URL/credentials, persistent volume) in `application-prod.yml`.

### F-14 [INFO] Sequential `Long` IDs on admin endpoints
- **Checklist:** §7 Access control — unguessable IDs
- **Status:** N/A → noted
- **Evidence:** `User.java:24-26` (`GenerationType.IDENTITY`); `/api/admin/users/{id}` is ADMIN-gated and `AdminService` intentionally operates on any non-self account — IDOR is not applicable since object-level ownership does not exist for admin operations.
- **Remediation:** none required; note only that unguessability is not the control here — the role check is.

### F-15 [INFO] No absolute session lifetime cap
- **Checklist:** §2 Authentication & session management
- **Status:** NOT VERIFIED → by design
- **Evidence:** `application.yml:20` pins idle timeout `30m`; Spring Session JDBC applies no absolute TTL — a continuously active session never expires.
- **Remediation:** acceptable for the spec; if a hard cap is needed, enforce max age via `DefaultCookieSerializer`/`Session` maxInactiveInterval policy at login time.

### F-16 [INFO] Admin seed password below policy seeds anyway (WARN only)
- **Checklist:** §2 Authentication & session management — password policy
- **Status:** PASS with note
- **Evidence:** `AdminSeeder.java:87-96` — a short `APP_ADMIN_PASSWORD` logs a warning but still seeds. `ProdAdminCredentialsValidator` enforces presence, not strength.
- **Remediation:** optionally extend `ProdAdminCredentialsValidator` to also enforce `password-min-length` in `prod` (fail fast instead of warn).

### F-17 [INFO] Password policy is length-only (≥12), no breach-list screening
- **Checklist:** §2 Authentication & session management — password policy
- **Status:** PASS (current-guidance compliant)
- **Evidence:** `UserService.java:30-33`, `app.password-min-length: 12` (`application.yml:47`). Consistent with "prefer length over rotation/complexity"; no k-anonymity breach check.
- **Remediation:** optional — screen against a breach corpus (e.g., HaveIBeenPwned range API) at registration/reset.

## Checklist coverage

| §   | Area                                     | Result                                            |
|-----|------------------------------------------|---------------------------------------------------|
| 1   | Input validation & output sanitization   | PASS — `@Valid` DTOs w/ `@Size`/`@Email` ceilings, enum binding; parameterized JPQL only; zero injection sinks |
| 2   | Authentication & session management      | PASS — BCrypt, HttpOnly+SameSite=Lax cookie, 30m idle, rotation on login, server-side logout, 2-layer throttle; F-09/F-17 noted |
| 3   | HTTPS everywhere                         | NOT VERIFIED — no TLS in repo; HSTS/Secure pending proxy deployment (documented assumption) |
| 4   | CSRF                                     | PASS — cookie token + header required on all mutations (verified 401/403 without it), SameSite=Lax both cookies, rotation on login/logout |
| 5   | XSS                                      | PASS — zero raw-HTML sinks in `frontend/src`; CSP `default-src 'none'` on the JSON API; SPA-host CSP is a manual item |
| 6   | Security headers                         | PASS — full set verified live; no `Server`/`X-Powered-By`; HSTS pending §3 |
| 7   | Access control / authorization           | PASS — default-deny, `hasRole("ADMIN")`, self-action guard, session invalidation on privilege loss, explicit CORS allow-list + credentials |
| 8   | Data protection                          | PASS — BCrypt, SecureRandom+SHA-256 reset tokens, no secrets in repo; F-10/F-13 noted |
| 9   | Error handling & logging                 | PASS — RFC 7807, identical auth-failure bodies, generic token rejection, structured JSON audit; F-12 accepted |

## Manual review items

1. **TLS termination + `server.forward-headers-strategy=framework`** — the IP throttle keys on `getRemoteAddr()` and the XSRF cookie's `Secure` flag derives from `request.isSecure()` (`IpThrottleService.java:46-49`, `SecurityConfig.java:216-224`). Without forwarded-header handling behind a proxy: every client shares the proxy's throttle bucket (self-DoS) and the XSRF cookie loses `Secure`. Then verify `Strict-Transport-Security` over `https://`.
2. **SPA static-host headers** — CSP/Referrer-Policy/etc. for `index.html` belong wherever the frontend is served; the API's `default-src 'none'` cannot cover it.
3. **Real mail adapter** — `EmailService` is a stub; suppressed mode in prod means no delivery channel exists at all.
4. **Alerting on the audit stream** — events are emitted as JSON lines; no monitoring/alerting is wired (ops gap).
5. **Prod datastore + org password policy** — see F-13, F-17.

## Top 3 remediations (priority order)

1. **Wire deployment TLS + forwarded headers** (manual): terminate HTTPS, set `server.forward-headers-strategy=framework`, confirm HSTS and `Secure` on both cookies end-to-end. This is the only gap that would actively break shipped controls.
2. **Flip `app.password-reset.log-reset-link` to `false` in `application.yml`** and enable it in `application-dev.yml` instead (F-10) — removes the only path where a live credential can reach logs by default.
3. **Consistency sweep** (F-09, F-11): gate `password-reset/confirm` with the anon throttle like its siblings, and delete the stale `static/` bundle so a divergent SPA can never ship by accident.
