# Browser acceptance run — assessment-prd.md (chrome-devtools + Playwright CLI)

Date: 2026-09-27. Stack: `java -jar backend/target/hello-auth-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev` (:8080) + `npm run dev` (:3000).
Drivers: chrome-devtools MCP (interactive) and Playwright CLI scripts (`e2e/browser-acceptance-{a,b,c}.mjs`, Chromium headless shell). Screenshots: `screenshots-cli/`. Raw results: `screenshots-cli/results-a.json`, `results-b.json`. Backend audit log observed on backend stdout.

## Story-by-story results

| PRD story | Result | Evidence |
|---|---|---|
| 1. Register (unique user/email, ≥12 pw, BCrypt) | PASS | `dave_chrome` registered via UI → landed on `/` with "Hello, dave_chrome" (shot a01). Duplicate username → "Username 'carol_chrome' is already taken."; duplicate email → "Email 'carol_chrome@example.com' is already registered." Weak pw (5 chars) blocked client-side: "Please lengthen this text to 12 characters or more" (server-side policy covered by MockMvc suite). |
| 2. Login (generic failure, session cookie) | PASS | Correct creds → greeting. Wrong password AND unknown username → identical "Invalid username or password." Old password rejected after reset; new password accepted (shot b03). |
| 3. Lockout + IP throttle | PASS (throttle live; lockout test-covered) | Repeated bad logins → "Too many failed login attempts. Try again later."; a subsequent attempt with *correct* credentials still rejected (429 wall), proving IP-level throttle independent of account state (shots s03, b04). Per-account lockout unreachable from one source IP by design (`ip-throttle.max-failures 4 < lockout.max-failures 5` anti-DoS invariant) — covered by MockMvc tests varying remoteAddr. |
| 4. Logout + server-side invalidation | PASS | Sign out → `/login`; `GET /api/hello` with credentials → 401; replayed pre-logout `SESSION` cookie → 401. |
| 5. Protected greeting | PASS | Authed `GET /api/hello` → "Hello, carol_chrome"/"Hello, dave_chrome"; anonymous → 401; session survives full page reload. |
| 6. Reset request (generic response) | PASS | `nobody@example.com` and `carol_chrome@example.com` produced identical confirmation text ("If an account with that email…"); only carol's triggered a stub-logged link on backend stdout (shot a05). |
| 7. Reset confirm (single-use, session purge) | PASS | Emailed link → `/reset-password?token=…` → "Password updated"-style confirmation (shot b01). A second live session for carol returned 200 on `/api/hello` before the confirm and 401 after — all sessions invalidated. Token replay → "This reset link is invalid or has expired." (shot b02). Missing token → "missing its token" message. |
| 8. Admin user list | PASS | `/admin` lists username, email, role, enabled, created-at; no password hashes (shot a02). USER calling `GET /api/admin/users` → 403; USER navigating to `/admin` → bounced to `/`. |
| 9. Enable/disable | PASS | Admin disabled `carol_chrome` → row shows Disabled (shot a03); carol login rejected with generic error; re-enable → login works again. |
| 10. Role change | PASS | `Make admin` → row shows ADMIN; `Revoke admin` → back to USER. |
| 11. Delete | PASS | `victim_user` deleted after confirm dialog; row removed (shot a04). |
| 9–11 self-guards | PASS | Acting admin's own row renders all 3 action buttons disabled — self-disable/demote/delete unreachable from the UI (server-side guard additionally covered by tests). |
| 12. Admin bootstrap | PASS (partial) | Fresh boot logged `Seeded initial admin account 'admin'`; admin login + panel verified. Dedup-on-restart not verifiable live (dev H2 is in-memory; restart re-seeds by design). |
| Route guards | PASS | Anonymous `/` → `/login`; authed user at `/login` → `/`; authed user at `/forgot-password` → `/`; USER at `/admin` → `/`. |
| CSRF | PASS | `POST /api/auth/register` and `/api/auth/login` without `X-XSRF-TOKEN` → 403. |
| Audit logging | PASS | Structured JSON events on stdout: `login_success`, `login_failure` (reason: `disabled`), `admin_status_changed`, `admin_role_changed`, `admin_user_deleted`, `password_reset_requested` — actor + target fields, no passwords/tokens. |
| Cookie/security headers | PASS | `SESSION` cookie `HttpOnly; SameSite=Lax` (dev; prod adds `Secure` + `Strict`). API responses carry CSP `default-src 'none'; frame-ancestors 'none'`, `x-content-type-options: nosniff`, `x-frame-options: DENY`, referrer/permissions policies, and credentialed CORS `access-control-allow-origin: http://localhost:3000`. |

## Not browser-verifiable (covered by the MockMvc suite)

- Per-account lockout trigger and post-cooldown reset (single source IP always trips the throttle first — intended).
- Expired reset tokens (15-minute wall-clock TTL).
- Admin-seed dedup across restarts (in-memory dev datastore).
- `Secure` cookie attribute and HSTS (prod-only, HTTPS deployment).

## Notes

- chrome-devtools MCP was used for the initial interactive pass (register/login/throttle verified live in Chrome); `evaluate_script`/`get_network_request` calls intermittently stalled, so the full suite was completed via Playwright CLI driver scripts kept at `e2e/browser-acceptance-*.mjs`.
- The 10-minute IP-throttle window is in-memory; the backend was restarted between run segments to clear it — acceptable in dev (H2 in-memory resets too).
- `GET /api/auth/me` 401s during anonymous route-guard probes are expected, not defects.
