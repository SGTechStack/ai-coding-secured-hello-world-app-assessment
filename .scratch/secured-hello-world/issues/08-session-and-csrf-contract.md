# 08 — Decide session management and the CSRF contract

Type: grilling
Status: open
Blocked by: 01, 02, 04, 05, 20

## Question

How do sessions behave end to end, and what is the exact CSRF contract between the SPA on one origin
and the API on another?

## Settled going in

The App Standard's `[Enforced Constraint]` settles the part that looked hardest: **Synchronizer
Token Pattern bound to the HTTP session** via `HttpSessionCsrfTokenRepository`, delivered by a
dedicated non-cacheable endpoint. `CookieCsrfTokenRepository` (double submit) is **prohibited**.
Spring Session JDBC is the store. Cookies are `HttpOnly`, `Secure`, `SameSite=Lax`.

Also settled and worth restating because it is counter-intuitive: **logout is not CSRF-exempt.** Its
CSRF token is session-bound, so logout on an expired session correctly returns 401/403, and the SPA
must absorb that rather than the server relaxing protection. The standard says so explicitly.

## What to decide

**Session lifecycle.**

- Idle timeout: standard default 15 minutes. Confirm, or justify a different value.
- Absolute lifetime: standard default 8 hours. Spring Session has no first-class absolute-lifetime
  knob, so decide the mechanism (a creation-time attribute checked by a filter? a custom
  `SessionRepository` wrapper?) using "Pin down the Spring Security 7 config surface".
- Maximum concurrent sessions: standard default 1, meaning a second login kills the first. Confirm.
  Decide which session dies — the standard says the *earlier* one is invalidated, so a new login
  always wins. Note the UX consequence: two browser tabs are fine, two devices are not.
- Session fixation: ID rotation on login. Spring Security does this by default; confirm it survives
  the custom JSON authentication filter.
- What invalidates every session for a user, and where that is centralised: password reset, password
  change, admin disable, admin role change (does changing someone's role force re-login? decide),
  admin delete.

**CSRF contract.**

- The token endpoint: path, method, response body shape, cache headers. The standard requires no
  caching.
- Header name for submission — `X-CSRF-TOKEN` per the standard's note.
- **Bootstrap ordering**, the subtle bit: the SPA needs a CSRF token to POST to login, but the token
  is session-bound and there is no session before login. Decide the sequence: does an anonymous
  session get created to carry the pre-login token, and if so, does the session ID rotate on
  successful login while the CSRF token is also rotated? Check
  `Standalone_Session_Login_with_CSRF_Bootstrap.md` — it likely prescribes this exactly.
- Token rotation policy: on login, on logout, per request, or never within a session.
- What the SPA does on a 403 from an expired token: silently re-fetch and retry once, or bounce to
  login? This decision is consumed by "Design the frontend architecture".

**Cross-origin specifics.** `localhost:3000` and `localhost:8080` are cross-*origin* but same-*site*,
so `SameSite=Lax` cookies do travel — the naive "we need `SameSite=None`" reasoning is wrong. Confirm
this holds for the intended production topology too, and record the constraint it places on
deployment (frontend and API must remain same-site, i.e. same registrable domain). If they ever
won't be, `SameSite=Lax` breaks and that is a deployment-blocking fact worth writing down now.

**Per-profile cookie config.** `Secure=true` is mandated but local dev is HTTP. Decide how the
profile split works without the production path ever being able to ship with `Secure=false`.

## Done when

Timeouts, concurrency, invalidation triggers, the CSRF bootstrap sequence, rotation policy, and the
same-site deployment constraint are all recorded, with the profile split specified.
