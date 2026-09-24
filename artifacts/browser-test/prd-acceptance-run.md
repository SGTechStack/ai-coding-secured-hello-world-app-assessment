# Browser acceptance run — assessment-prd.md

Date: 2026-09-16. Stack: `spring-boot:run` (dev profile, :8080) + `npm run dev` (:3000).
Driver: Playwright MCP (real Chromium). Screenshots: `s05-hello-alice.png`, `s06-admin-panel.png`.

## Story-by-story results

| PRD story | Result | Evidence |
|---|---|---|
| 1. Register (unique user/email, ≥12 pw, BCrypt) | PASS | Registered `alice`; duplicate username → "Username 'alice' is already taken." Short pw blocked by client minLength; server-side enforcement covered by tests. |
| 2. Login (generic failure, session cookie) | PASS | Correct creds → `/` greeting. Wrong/old pw → generic "Invalid username or password." |
| 3. Lockout + IP throttle | PASS (throttle live; lockout test-covered) | 4 IP-window credential failures → 5th attempt returns 429, UI shows "Too many failed login attempts. Try again later." Account-lock path unreachable from one IP by design (`4 < 5` invariant); covered by integration tests varying remoteAddr. |
| 4. Logout + server-side invalidation | PASS | Sign out → `/login`; revisit `/` → bounced to `/login`. |
| 5. Session persistence | PASS | Reload on `/` stays signed in ("Hello, alice"). |
| 6. Reset request (generic response) | PASS | Unknown `nobody@example.com` and registered `alice@example.com` produce identical message; only alice's triggers the stub-logged link. |
| 7. Reset confirm (single-use, session purge) | PASS | Confirm → "Password updated." Alice's live session died (login page probe 401). Token replay → "invalid or has expired." Old pw rejected, new pw accepted. |
| 8. Admin panel (list/disable/role/delete, self-guard) | PASS | `/admin` lists users; self-row buttons disabled; disable → alice login rejected generically; re-enable → login works; USER→ADMIN→USER verified; `bob` deleted after confirm dialog. |
| Route guards | PASS | Anonymous `/` → `/login`; USER at `/admin` → bounced to `/`; authed user at `/login` → bounced to `/`. |
| CSRF | PASS | Register POST without `X-XSRF-TOKEN` → 403 (from page context). |
| Audit logging (ticket 14) | PASS | JSON events observed on stdout: login_success, login_failure (reasons: bad_credentials, disabled, ip_throttled + remote_addr), password_reset_requested/completed, admin_status_changed, admin_role_changed, admin_user_deleted — actor/target present, no secrets. |

## Notes

- `/api/auth/me` 401s in console during anonymous session probes are expected (route-guard design), not defects.
- `lockout_triggered` audit event not reachable in-browser: IP throttle (4) trips before per-account lockout (5) from a single IP — the intended anti-DoS property. Verified by `LoginService` integration tests instead.
- `nosuchuser` failures produced no account and no existence signal — generic 401 identical to real-user failures.
