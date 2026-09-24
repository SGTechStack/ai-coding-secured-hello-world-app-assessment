# Browser Acceptance Test — Hello World Auth

**Date:** 2026-09-22
**Acceptance criteria:** [`assessment-prd.md`](../../assessment-prd.md)
**Driver:** Playwright MCP (Chromium), plus in-browser `fetch` for API-level assertions and curl for raw-header evidence.
**Deployment under test:** single-origin — the Vite bundle is served by the Spring Boot backend at `http://localhost:8080` (`frontend/dist` → `backend/src/main/resources/static`, packaged into `hello-auth-backend-0.0.1-SNAPSHOT.jar`, run with `--spring.profiles.active=dev --app.password-reset.link-base-url=http://localhost:8080/reset-password`).
**Backend log:** [`logs/backend.log`](logs/backend.log) · **Screenshots:** [`screenshots/`](screenshots/)

## Build provenance note

The jar under test was built 2026-09-22 16:24 and embeds an SPA-hosting variant of the security config (`SpaForwardController`, self-referential CSP, non-API GET permitted) that is **not present in the current source tree** — the current `SecurityConfig.java` (modified 16:38, matching git HEAD) is the API-only posture (`anyRequest().authenticated()`, `default-src 'none'` CSP). Rebuilding from source as-is would produce a jar that cannot host the SPA (401 on `/`, CSP would block all scripts). The frontend bundle inside the jar is byte-identical to the current `frontend/dist` output.

## Results

| # | PRD ref | Test case | Result | Screenshot |
|---|---------|-----------|--------|------------|
| TC01 | S5 AC-b | Anonymous visit to `/` redirects to `/login`; session probe `GET /api/auth/me` → 401 | PASS | [tc01](screenshots/tc01-anonymous-redirect-login.png) |
| TC02 | S1 AC-a, S5 AC-a | Register `alice` (valid username/email/12+ char password) → account created, auto-login, lands on `/` showing `Hello, alice` | PASS | [tc02](screenshots/tc02-register-alice-hello.png) |
| TC03 | S4 AC-a/b, S5 AC-b | Sign out → back on `/login`; replayed pre-logout `SESSION` cookie → `GET /api/hello` **401**; anonymous `GET /api/hello` → 401 | PASS | [tc03](screenshots/tc03-logout-back-to-login.png) |
| TC04 | S1 AC-b | Register duplicate username `alice` → `Username 'alice' is already taken.` (409) | PASS | [tc04](screenshots/tc04-duplicate-username.png) |
| TC05 | S1 AC-b | Register duplicate email `alice@example.com` → `Email 'alice@example.com' is already registered.` (409) | PASS | [tc05](screenshots/tc05-duplicate-email.png) |
| TC06 | S1 AC-c | Password `short` — client `minLength` blocks the submit; direct `POST /api/auth/register` → **400** `Password must be at least 12 characters.`; no account created | PASS | [tc06](screenshots/tc06-weak-password-blocked.png) |
| TC07 | S2 AC-a, NFR | Login `alice` correct creds → session created, `SESSION` cookie `HttpOnly; SameSite=Lax` (`Secure=false`, dev profile per PRD), greeting `Hello, alice` | PASS | [tc07](screenshots/tc07-login-alice-success.png) |
| TC08 | S7 AC-a | Password reset confirmed (API) while alice's session live → that session now returns **401** on `/api/auth/me`; SPA bounces to `/login` | PASS | [tc08](screenshots/tc08-reset-invalidated-session-bounced.png) |
| TC09 | S6 AC-a/b | Reset request for registered `alice@example.com` → generic `If an account with that email exists, we've sent a reset link.`; reset link logged by stubbed EmailService with `:8080` URL | PASS | [tc09](screenshots/tc09-reset-request-registered-email.png) |
| TC10 | S6 AC-a | Reset request for unregistered `nosuchuser@example.com` → **identical** generic message (enumeration-resistant) | PASS | [tc10](screenshots/tc10-reset-request-bogus-email-identical.png) |
| TC11 | S7 AC-a | bob's logged link `/reset-password?token=…` → set new password → `Password updated. Sign in with your new password.` | PASS | [tc11](screenshots/tc11-reset-confirm-success.png) |
| TC12 | S7 AC-c | Reuse the same token → `This reset link is invalid or has expired.` (400, single-use) | PASS | [tc12](screenshots/tc12-reset-token-reuse-rejected.png) |
| TC13 | S7 AC-a | bob logs in with the **new** password → `Hello, bob` | PASS | [tc13](screenshots/tc13-bob-new-password-login.png) |
| TC14 | S8 AC-a, S12 | Seeded `admin` logs in → `/admin` lists every user (username, email, role, enabled status, created date); **no password hashes**; admin's own row controls disabled | PASS | [tc14](screenshots/tc14-admin-user-list.png) |
| TC15 | S10 AC-a | Admin clicks `Make admin` on bob → row flips to `ADMIN`/`Revoke admin`; `Revoke admin` flips it back to `USER` | PASS | [tc15](screenshots/tc15-bob-promoted-admin.png) |
| TC16 | S9 AC-a | Admin clicks `Disable` on bob → status `Disabled`; bob login with correct password → **401** `Invalid username or password.` | PASS | [tc16](screenshots/tc16-bob-disabled.png) |
| TC17 | S11 AC-a | Admin clicks `Delete` on bob → confirm dialog `Delete bob? This cannot be undone.` → accept → row removed | PASS | [tc17](screenshots/tc17-bob-deleted-row-gone.png) |
| TC18 | S8 AC-b | Signed in as `alice` (USER): `fetch /api/admin/users` → **403**; navigating to `/admin` bounces to `/` | PASS | [tc18](screenshots/tc18-alice-admin-route-bounced.png) |
| TC19 | S2 AC-b | Login `alice` + wrong password → `Invalid username or password.` (401) | PASS | [tc19](screenshots/tc19-login-wrong-password-generic.png) |
| TC20 | S2 AC-b | Login `nosuchuser` → **identical** `Invalid username or password.` — username existence not revealed | PASS | [tc20](screenshots/tc20-login-unknown-user-identical.png) |
| TC21 | S7 AC-a | alice's pre-reset password `Str0ng!Passw0rd99` → 401 (password genuinely rotated) | PASS | [tc21](screenshots/tc21-alice-old-password-rejected.png) |
| TC22 | S3 AC-c | After 4 recorded IP failures, alice's **correct** password → `Too many failed login attempts. Try again later.` (429) — IP throttle independent of account state | PASS | [tc22](screenshots/tc22-ip-throttled-correct-creds.png) |

## API-level checks (browser `fetch` / curl)

| Check | Result |
|-------|--------|
| Admin self-action guard (S9/S10/S11 AC-b) — `PATCH /status`, `PATCH /role`, `DELETE` on admin's own id while logged in as admin | All three → **400** `Cannot modify own account` |
| CSRF — `POST /api/auth/login` **without** `X-XSRF-TOKEN` | **403** (rejected before credential check) |
| Cookie attributes | `SESSION`: HttpOnly, SameSite=Lax, Secure=false (dev). `XSRF-TOKEN`: JS-readable, SameSite=Lax |
| Response headers on `/` | `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, `X-Frame-Options: DENY`, CSP `default-src 'self' … frame-ancestors 'none'`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy: camera=(), microphone=(), geolocation=()` |
| Plaintext passwords in backend log (S1 AC-d) | None of the 9 passwords used in testing appear anywhere in `backend.log`; the dev-seed admin password also never appears |
| Audit log (NFR) | Structured JSON lines for `login_success`, `login_failure` (`bad_credentials`/`disabled`/`ip_throttled`), `password_reset_requested`, `password_reset_completed`, `admin_role_changed`, `admin_status_changed`, `admin_user_deleted` — all with actor/target, no secrets |

## Not browser-verifiable in this run (covered by the MockMvc suite)

| AC | Why not | Evidence |
|----|---------|----------|
| S3 AC-a/b — per-account lockout at 5 failures + unlock after cooldown | By design `ip-throttle.max-failures (4) < lockout.max-failures (5)`: a single source IP can never accumulate enough failures to lock an account (the anti-DoS invariant). Triggering it needs multiple source IPs or a stubbed `Clock` | `LockoutAndThrottleApiTests`, `AuthFlowTests` (MockMvc) |
| S2 AC-c — locked account rejects correct creds | Same reason — no account can be put into `locked_until` from one IP | backend suite |
| S7 AC-b — expired token rejected | Token TTL is 15 min wall-clock; no time-travel seam in the live app | backend suite |
| S12 AC-b — restart creates no duplicate admin | Dev H2 is in-memory `create-drop` — a restart always reseeds from empty, so dedup is unobservable | `AdminSeeder` logic + backend suite |

## Environment notes

- IP throttle state at end of run: `127.0.0.1` is throttled until ~10 min after the last failure — further logins will 429 until the window decays (or restart the backend).
- Backend is still running: `java -jar backend/target/hello-auth-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev` on `:8080`. Log: `artifacts/browser-test/logs/backend.log`.
