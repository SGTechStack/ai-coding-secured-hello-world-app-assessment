# 12: Password reset flow

**What to build:** A user who forgot their password can self-serve a reset. `POST /api/auth/password-reset/request` accepts an email and always returns the generic `"If an account with that email exists, we've sent a reset link."` — registered or not. When the email matches, a 32-byte `SecureRandom` token (base64url) is generated, its SHA-256 hash stored with a 15–30 min `expires_at`, and the stub `EmailService` logs the reset link. `POST /api/auth/password-reset/confirm` accepts token + new password (≥ 12): validates unexpired/unused, updates the BCrypt hash, marks `used_at`, and invalidates ALL the user's sessions via `findByPrincipalName` + `deleteById`. The SPA has forgot-password and reset-password pages (the logged link's target).

**Blocked by:** 09: Auth spine — register → login → hello

**Status:** implemented

- [x] Request endpoint returns the identical generic success message for registered and unregistered emails
- [x] Registered email → token created, hash (never plaintext) stored with short expiry, EmailService stub logs the link
- [x] Valid token + compliant password → password updated, `used_at` marked, all existing sessions for that user invalidated (old cookie → 401)
- [x] Expired token → rejected, password unchanged
- [x] Reused token → rejected (single-use enforced)
- [x] New password < 12 → rejected
- [x] SPA: forgot-password page submits email; reset-password page accepts the token from the logged link and completes the flow end-to-end

Implementation notes: everything lives in a new `passwordreset` package —
`PasswordResetToken` entity (the ratified `password_reset_tokens` shape:
`user_id` FK via `@ManyToOne`, unique `token_hash`, `expires_at`,
`used_at`), `PasswordResetTokenRepository.findByTokenHash`,
`PasswordResetService`, `PasswordResetController`
(`/api/auth/password-reset/request|confirm`), `EmailService` stub, DTO
records (`PasswordResetRequest{email}`, `PasswordResetConfirm{token,
newPassword}`, `MessageResponse`), and `InvalidResetTokenException` → 400
`problem+json` via `ApiExceptionHandler` (one generic detail for
unknown/spent/expired — token state is not public information).

Token mechanics per ticket 07: 32 `SecureRandom` bytes → base64url
(no padding) into the link; only the SHA-256 hex is stored or queried
(`PasswordResetService.hashToken`, public so tests seed rows through the
repository — the ratified fixture seam). TTL is `app.password-reset
.token-ttl` (default 15m — the tight end of the spec's 15–30 bound);
`app.password-reset.link-base-url` (default
`http://localhost:3000/reset-password`) feeds the stub, which logs
`<base>?token=<token>` at INFO — the only place the plaintext is emitted.

`confirmReset` is `@Transactional`: password re-BCrypt + `used_at` are
atomic, and the per-user session purge
(`findByPrincipalName(username)` → `deleteById`, the ticket-02 mechanism)
runs on the same datasource transaction. Order inside: token lookup →
unused/unexpired check → password-length policy (reuses
`RegistrationException.PasswordTooShort` → same 400 "Password too short"
shape as registration) → apply. A failed confirm never consumes the token,
so a typo'd password doesn't burn the link. Lockout counters are left
untouched on reset (not in scope; `locked_until` still expires on its own).

Test seams used: primary HTTP seam (`PasswordResetFlowTests` — `MockMvc` +
`@SpringBootTest` on H2, real `GET /api/auth/csrf` → `X-XSRF-TOKEN` flow,
`@Primary` `MutableClock` for expiry, users/tokens seeded via repositories,
session invalidation proven by replayed-cookie → 401 on `/me` and
`/hello`). The emailed plaintext token is captured at the `EmailService`
port via `@MockitoBean` — its only egress — giving a true request→confirm
end-to-end cycle; `verifyNoInteractions` pins the unknown-email case. The
stub's log line itself is pinned at the narrow seam (`EmailServiceTests`,
Logback `ListAppender`). No service-level seam needed — no ordering
invariant like ticket 11.

Frontend: `api.requestPasswordReset`/`confirmPasswordReset` reuse
`postJson` (CSRF header attached automatically); `ForgotPasswordPage`
(`/forgot-password`) submits an email and displays the server's generic
message verbatim; `ResetPasswordPage` (`/reset-password?token=…` — the
logged link's target) reads the token from `useSearchParams`, submits the
new password, and shows ApiError `detail` verbatim; both routes sit behind
`RedirectIfAuthed`; LoginPage gained a "Forgot your password?" link.

Trap worth noting: on real Tomcat a tokenless mutating POST answers 401
(anonymous → `HttpStatusEntryPoint`), while the same request under MockMvc
answers 403 — uniform across all endpoints (pre-existing, e.g.
`/api/auth/login`), so CSRF-on tests keep asserting 403 at the MockMvc
seam and the live check observes 401.

## Comments

**Verification (do-work-min, 2026-09-16):**

- Implemented: `POST /api/auth/password-reset/request|confirm` — generic
  response for known/unknown emails (byte-identical bodies asserted), 32-byte
  SecureRandom plaintext → base64url, SHA-256 hex stored only, 15m TTL via
  `Clock`, single-use `used_at`, re-BCrypt, per-user session purge via
  `findByPrincipalName`+`deleteById` (replayed cookie → 401), `EmailService`
  stub logs the link at INFO. SPA forgot/reset pages wired end-to-end.
- Verification steps: `mvn test` 95/95 green across 16 classes
  (`PasswordResetFlowTests` 9/9, `EmailServiceTests` 1/1); `npm run build` +
  `oxlint` clean; live curl on port 8082 (8080 held stale): generic 200s,
  logged reset link, confirm 200, replayed session 401, old password 401 /
  new 200, reused token 400, short password 400, tokenless POST rejected.
- Reviewer loop: Must-fix=0; one human decision — reset-token housekeeping
  (one-live-token, atomic consume, table cleanup) deferred to ticket 14 per
  the "document now, enforce at hardening" precedent
  (`docs/agents/reviewer-decisions.md`).
- Final gate: all four checks PASS — semgrep 0 findings; thermo-nuclear 3
  Low/2 Nit (password-policy duplication `UserService.register` vs
  `confirmReset`, hardcoded `PASSWORD_MIN_LENGTH` in ResetPasswordPage,
  public `hashToken` fixture seam); spring-security/spring-web PASS.
  Aggregate **PASS**; report
  `artifacts/code-reviewer/12-password-reset-flow-compliance.html`.
- Mutation gate: 98% killed (49/50). Sole survivor is equivalent —
  `StringBuilder(digest.length * 2)` mutated to `/ 2` changes only the
  capacity hint, not output; report
  `artifacts/mutation-testing/12-password-reset-mutation.md`.
- Not yet (later tickets): admin module (13); audit logging, prod profile,
  token housekeeping, config-invariant guard (14).
