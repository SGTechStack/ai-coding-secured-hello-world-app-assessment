# PRD conformance

A pass over `prd/assessment-prd.md` checked **against the PRD itself**, not against the plan derived
from it. That distinction is the reason this document exists: the plan's own required-test list was
green while four of the PRD's requirements had no assertion behind them.

Read [`../.scratch/secured-login-app/handoff/prd-deltas.md`](../.scratch/secured-login-app/handoff/prd-deltas.md)
first. Several things below differ from the PRD deliberately, and without that document they read as
defects.

---

## Non-functional and security requirements (`prd:112-123`)

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 1 | **Password storage** — BCrypt, no custom hashing | met | `PasswordEncoderConfig` returns `BCryptPasswordEncoder`; no other hashing exists. `AuditSchemaAndBootstrapIT` asserts a stored hash starts `$2a$12$` |
| 2 | **Session security** — `HttpOnly`, `Secure`, `SameSite`; session-fixation protection; sessions invalidated on logout and password reset | met | `SessionCookieIT` (attributes incl. `Path=/`, rotation on login, post-logout replay refused), `SecurityCriticalIT`, `PasswordManagementIT` (a session live before a reset is dead after it) |
| 3 | **CSRF** — every state-changing endpoint | met | `SecurityCriticalIT`: the four anonymous endpoints, **and** the administrator `PATCH`/`DELETE` mutations |
| 4 | **CORS** — explicit allow-list, `Allow-Credentials: true` | met | `SecurityCriticalIT`: allowlisted origin echoed, non-allowlisted refused with no echo, preflight for a `PATCH` answered |
| 5 | **Enumeration resistance** — login and reset-request reveal nothing | met | identical generic `401` with empty body; identical reset-request body; **and** identical audit lines, which is the half a response-only test misses |
| 6 | **Transport** — HTTPS in any real deployment; local dev over HTTP an accepted gap | met, as documentation | `limitations.md` §3. `Secure` is set in every profile, which is stricter than the PRD asks |
| 7 | **Audit logging** — the named events, actor + target, never passwords | met | `AuditSchemaAndBootstrapIT`: all seven account-lifecycle events assert actor **and** target separately; a closed-vocabulary test; and no line carries a username, email, password, hash or token |
| 8 | **Least privilege** — server-side role checks, never from client state | met | the authorization matrix is the sole mechanism; no `@PreAuthorize` exists. `SecurityCriticalIT` walks the `USER`/`USER_MANAGER` rows. The SPA reads `role` from `GET /currentUser` for rendering only |

## Data model (`prd:125-150`)

Both tables are an exact **superset** of the PRD.

- `users` — every PRD column present. Additions: `disabled_at`, `last_login_at`,
  `require_password_change`, all sanctioned in `prd-deltas.md`.
- `password_reset_tokens` — matches exactly: `id`, `user_id`, `token_hash`, `expires_at`, `used_at`.

`AuditSchemaAndBootstrapIT` also asserts there is **no** `account_non_locked` column: two sources of
truth for "is this account locked" would be a silent authentication bypass.

## Required test coverage (`prd:151-160`)

| Requirement | Status |
|---|---|
| Login: success, wrong password, unknown username (identical error), locked account | met |
| Lockout: N failures locks; login after cooldown resets the counter; **IP throttling independent of account lockout** | met |
| Logout: a reused session cookie is refused | met |
| Password reset: single-use, expiry, **reset invalidates existing sessions** | met |
| Admin self-action guard: cannot disable, demote or delete own account | met |
| Role enforcement: a `USER` calling an admin endpoint gets `403` | met |

---

## The four gaps this pass found

Recorded because they are the argument for checking the PRD directly rather than trusting a derived
plan. All four are now closed.

1. **IP throttling had no test at all** (`prd:150`) — the case the PRD argues for most explicitly.
   Writing it surfaced a property of the limiter: Bucket4j refills greedily at ~1 token/1.2s and a
   failed login pays the 250ms response floor, so a *serial* caller drains ~0.75 tokens per attempt
   and 60 attempts do not trip a limit of 50. Correct for a rate limiter — it shapes rate, not total —
   but the test now sprays concurrently, which is both the real attack shape and honest about what the
   limiter defends against.

2. **A test asserted less than its name claimed.** `confirmCompletesTheFlow` was titled "kills every
   session" and never checked it. Worse than a missing test: it reads as covered.

3. **CORS was entirely untested** (`prd:120`), including `Allow-Credentials: true` — which is a
   credential leak if it ever pairs with a wildcard origin.

4. **The BCrypt cost factor was unasserted.** `startsWith("$2")` passes at the default strength of 10
   exactly as it does at the configured 12, so it proved the algorithm and nothing about the work
   factor — which is the security property.

## What conformance does *not* mean here

The PRD's functional scope, data model and required test coverage are complete. Its **acceptance
gates** are not: `im8-review`, `dependency-check-maven` and `browser-test` have not been run. See
[`limitations.md`](limitations.md) for why and what each needs.
