# Implementation Plan — Secured Hello World Auth

Vertical slices derived from [secured-hello-world-auth.md](./secured-hello-world-auth.md).

Tickets are tracked in this implementation plan rather than GitHub, as specified in [AGENTS.md](../../AGENTS.md).

Each slice ends at a **commit checkpoint**:
- the slice implementation is completed;
- the relevant tests are run and passing;
- the resulting changes are reviewed;
- the agent stops and provides a suggested commit message;
- the developer reviews and commits the changes before the next slice begins.

Completed slices should not be reopened or modified unless a genuinely blocking integration issue is discovered.

| Slice | Scope | Stories | Checkpoint |
|-------|-------|---------|-----------|
| **0** | Backend Maven scaffold: Spring Boot 3.5.x, security/CORS/CSRF baseline, Spring Session JDBC, H2, `Clock` bean, boots & compiles | infra | ✅ commit |
| **1** | Domain & persistence: `User` + `PasswordResetToken` entities (UUID), repositories, JPA/H2 schema | data model | commit |
| **2** | Registration: `POST /api/auth/register`, password policy (≥12, ≤72), BCrypt, username/email conflict, `ProblemDetail`, audit | 1–6 | commit |
| **3** | Login/logout/session/hello: custom login controller + `AuthenticationManager`, session-fixation, `/api/auth/csrf` (done in Slice 2), `/api/auth/me`, `/api/hello`, generic 401, audit. **+ Login timing resistance (dummy BCrypt), baseline security headers + session timeout + error hardening** | 7–9, 14–17, 37–38, **50, 53** | commit |
| **4** | Abuse controls: failed-attempt counting, Account Lockout (`locked_until`), Caffeine IP Throttling, `app.security.*` config. **+ Race-safe lockout** via pessimistic row lock | 10–13, **45** | commit |
| **5** | Password reset: request/confirm, token hash + expiry + single-use, all-session invalidation, stubbed `EmailService`. **+ CSPRNG token (≥256-bit) & SHA-256 hash, per-IP reset-request rate limit, `SessionInvalidator` helper, ADR-0002, reset link from configured base URL (Host-injection safe), invalidate prior unused tokens on new request** | 18–24, **46–47, 52** | commit |
| **6** | Admin: list, status toggle, role change, delete, self-action guard (409), role enforcement (403). **+ Session termination on disable/role-change (reuse `SessionInvalidator`), IDOR/BOLA negative tests, self derived from principal** | 25–34, **43–44, 49** | commit |
| **7** | Admin bootstrap: seed initial Admin from config, idempotent. **+ No usable default admin password; fail-fast on missing/placeholder in non-dev profile** | 35–36, **51** | commit |
| **8** | Frontend: React+Vite scaffold, pages (register/login/hello/forgot/reset), admin table, CSRF bootstrap, `/me` rehydrate | 41–42 | commit |
| **9** | Docs & final pass: run README, **cross-cutting sensitive-data security review (story 48): grep responses/logs/audit for secrets, ProblemDetail no-echo, actuator off**, tidy | **48** | commit |

Required integration tests (HTTP-boundary seam, `@SpringBootTest` RANDOM_PORT, real H2/Spring Session, `Clock` + `EmailService` doubles) are written **within** the slice that introduces the behavior — slices 3–7.

## Security hardening additions (approved 2026-09-29)

Cross-referenced to spec stories 43–49 and the "Security hardening mechanisms" section of [secured-hello-world-auth.md](./secured-hello-world-auth.md).

| # | Requirement | Slice | New tests |
|---|-------------|-------|-----------|
| 43 | Disable terminates existing Sessions | 6 | disabled user's live cookie → 401 on `/api/hello` and admin endpoints |
| 44 | Role change terminates existing Sessions | 6 | downgraded Admin's live cookie loses `ADMIN` access to `/api/admin/**` |
| 45 | Race-safe lockout (pessimistic lock) | 4 | N concurrent wrong-password attempts → Account locked, counter consistent |
| 46 | CSPRNG Reset Token, SHA-256 stored | 5 | tokens unique/high-entropy; stored hash ≠ plaintext |
| 47 | Per-IP reset-request rate limit | 5 | repeated requests from one IP → 429; generic 200 within limit regardless of email |
| 48 | No secrets in responses/logs/audit (cross-cutting) | 3/5/6 + 9 | ProblemDetail no-echo of credentials; log/audit capture asserts no secret |
| 49 | Admin authz from principal, no IDOR/BOLA | 6 | USER→403, anon→401, self-guard resolves self from principal not id |
| 50 | Login timing resistance (dummy BCrypt for unknown user) | 3 | unknown vs wrong-password identical 401; encoder invoked for non-existent user |
| 51 | No usable default admin password; fail-fast in non-dev | 7 | non-dev + placeholder/absent → startup fails; dev + configured → seeds once |
| 52 | Reset link from configured base URL (Host-injection safe) | 5 | captured link uses configured base URL despite attacker `Host` header |
| 53 | Baseline headers + session timeout + error hardening | 3 (verify 9) | security headers present on auth responses; timeout set; no stack trace in errors |

Added after the second (broader) security review, 2026-09-29. Case-insensitive identifiers, JSON-bomb DoS, and CI dependency scanning were reviewed and classified out of scope (documented in the spec's Out of Scope). All other checklist areas were found already covered by stories 1–49, the PRD, or Spring Security/Boot defaults (evidence in the review).

`SessionInvalidator` (wraps `FindByIndexNameSessionRepository`) is introduced in Slice 5 for password-reset all-session invalidation, then reused by Slice 6 for disable/role-change — so Slice 5 must precede Slice 6 (already the case). Slice 2 is **not** reopened; its no-password-in-response coverage is re-verified read-only in the Slice 9 review.
