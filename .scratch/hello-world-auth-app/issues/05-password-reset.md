# 05: Password reset (request + confirm)

**What to build:** A user who forgot their password can request a reset via their registered email and set a new password with the resulting token. The request endpoint always returns a generic success message regardless of whether the email is registered. When the email does match an account, a single-use reset token is generated, its hash (never the plaintext token) is stored with a short expiry (15–30 min), and the stub `EmailService` logs the reset link instead of sending real mail. The confirm endpoint validates the token (unexpired, unused), updates the password, marks the token used, and invalidates all existing sessions for that user.

**Blocked by:** 03 (needs working session infrastructure to invalidate on reset)

**Status:** done

- [x] Password-reset-request with any email → generic success response, regardless of whether the email is registered.
- [x] Password-reset-request with a registered email → single-use reset token generated, only its hash stored, expiry 15–30 min, `EmailService.sendPasswordResetEmail(...)` called (stub logs the link).
- [x] Password-reset-confirm with a valid, unexpired, unused token + a password meeting the strength policy → password updated, token marked used, all existing sessions for that user invalidated.
- [x] Password-reset-confirm with an expired token → rejected, password not changed.
- [x] Password-reset-confirm with an already-used token → rejected (single-use enforcement).
- [x] React app has working "forgot password" and "reset password" flows wired to the endpoints.

## Implementation notes

**Backend** (new `passwordreset` package)

- `PasswordResetToken` entity (`id`, `user` FK, `token_hash` unique, `expires_at`, `used_at`) + `PasswordResetTokenRepository`, matching the spec's data model.
- `EmailService`: stub, logs the reset link instead of sending mail.
- `PasswordResetService.requestReset`: always returns successfully from the caller's point of view, regardless of whether the email is registered (enumeration resistance) — only does real work (issuing a token, "sending" the email) when it matches an account. Generates a 32-byte `SecureRandom` token, SHA-256-hashes it for storage (a fast hash is correct here — this is a lookup key, not a password, so BCrypt's deliberate slowness would be the wrong tool), 30-minute expiry.
- `PasswordResetService.confirmReset`: validates the token exists, is unused, and is unexpired (in that order isn't load-bearing, but "already used" and "expired" get distinct messages); updates the password through the existing `PasswordPolicy` + `PasswordEncoder`; marks the token used; invalidates every session belonging to that user via `SessionRegistry`.
- `PasswordResetController`: `POST /api/auth/password-reset/request` and `/confirm`, both `permitAll()` in `SecurityConfig` (no session exists yet at this point in the flow).

**Real bug found and fixed via live testing** (following the same pattern as the CORS/cookie bugs in ticket 03): invalidating a user's sessions via `SessionRegistry.getAllSessions(...).forEach(SessionInformation::expireNow)` doesn't destroy the session immediately — Spring Security's default `ConcurrentSessionFilter` behaviour is to let the *next request* on that session through with **HTTP 200** and a plain-text "this session has been expired..." body, not a 401. That's the documented default, meant for browser page-reload flows, and it's wrong for a JSON API: a stale, reset-invalidated session would otherwise look like a successful response. Fixed with a new `RestSessionExpiredStrategy` (implements `SessionInformationExpiredStrategy`), wired via `.sessionManagement().expiredSessionStrategy(...)`, returning the same 401 `ErrorResponse` JSON shape as every other unauthenticated response in the app.
- This also required adding a `SessionRegistry` bean (`SessionRegistryImpl`) and registering `HttpSessionEventPublisher` as a servlet listener bean — both are prerequisites for the registry to actually track sessions per principal. `.maximumSessions(-1)` is used deliberately: it's for *tracking* sessions per user (so they can be force-expired later), not for *limiting* how many a user may have open at once.

**Frontend**

- `ForgotPasswordForm.tsx` (email → generic success message, plus an "I have a reset token" escape hatch) and `ResetPasswordForm.tsx` (token + new password → success message with a way back to login). `App.tsx` gained a small view switcher (`login` / `forgot-password` / `reset-password`) and reads a `?token=` query param on load to jump straight to the reset view, matching how a real emailed link would work.
- `api/client.ts` gained `requestPasswordReset` and `confirmPasswordReset`, both routed through the existing CSRF-header helper.

**Tests**

- `PasswordResetTest`: generic response is byte-identical for a registered vs. unregistered email; a request for a registered email produces exactly one token row with only a hash (never plaintext) and a future expiry; confirming with a valid token updates the password, marks the token used, and — the key assertion — a session that was live *before* the reset gets 401 on its next request *after* the reset; an expired token (constructed directly, since real-time expiry can't be waited out in a test) is rejected without changing the password; a token used twice is rejected the second time.
- The plaintext reset token only ever exists transiently inside the service method, passed straight to `EmailService`. Rather than reaching into token internals with reflection, the test uses `@MockitoSpyBean` on `EmailService` and an `ArgumentCaptor` to recover the real link the service generated — the same value a real email would contain.
- `mvn clean verify`: 20/20 tests pass, BUILD SUCCESS.
- Verified live end to end, both via direct `fetch` calls against the running server and by driving the actual React UI: registered an account, logged in, requested a reset, read the token from the backend's stub-email log line, confirmed the reset through the UI, confirmed the *pre-reset* session now gets 401 (not the default 200 expiry message), and logged back in successfully with the new password.
