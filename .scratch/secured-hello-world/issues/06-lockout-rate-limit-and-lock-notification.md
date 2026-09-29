# 06: Account lockout, per-username rate limit, and lock notification

**What to build:** After 5 consecutive wrong passwords, an Account becomes Locked for 20 minutes. Even the right password is refused, with the same generic error, and the Account holder is notified. More than 10 attempts per minute against one username get 429 with a "retry after" signal, and the SPA says "try again later". This ticket introduces the `EmailService` stub (with only the lock notification; tickets 08 and 09 add their own operations) and the rate-limiter infrastructure that later tickets reuse. See the spec's stories 25–30, 33–34 and 105, "Authentication guard", "Rate limiters", and "Notifications (`EmailService` stub)". Use `CONTEXT.md`'s Locked vs Disabled.

**Blocked by:** 04

**Status:** resolved

- [x] The failure counter increments on every wrong password. At 5 consecutive failures, `locked_until` is set 20 minutes ahead (configurable). Both are stored on the Account, so they survive a restart.
- [x] A Locked Account rejects even the correct password with the identical `authentication_failed` body. The lock lifts automatically when it expires, and a successful login resets the counter.
- [x] The Authentication guard runs in order: per-username rate limit → credential check → Locked/Disabled check → counter update. The IP Throttle slots in ahead of the rate limit in ticket 07.
- [x] Rate limiters are in memory with keyed buckets, and expose a reset operation used only by tests. Login is limited to 10 per minute per username (configurable). When exceeded: 429 with `Retry-After`, `code` `too_many_requests` and `detail` "too many requests".
- [x] The `EmailService` stub has one operation, "Account Locked", sent when an Account becomes Locked. It writes to its own logger and file, never the application or audit log, with the recipient masked.
- [x] Tests can swap in a recording `EmailService`.
- [x] Audit events: lockout (WARN, `access-control`, with the Account's UUID) and rate-limit breach (WARN, `access-control`, with the endpoint).
- [x] The SPA shows a "try again later" message on any 429.
- [x] Tests cover (Clock seam): 5 failures lock; a Locked Account rejects the correct password with the identical body; logging in after expiry succeeds and resets the counter; lockout persists across a context restart; 429 with `Retry-After`, `too_many_requests` and "too many requests"; the lock notification is recorded; and the audit events.

## Comments

### Verification (2026-09-29)

**Implemented:** `AuthenticationGuard` runs per-username rate limit → credential check → Locked/Disabled check → counter update. Five consecutive wrong passwords set `locked_until` 20 minutes ahead (`app.lockout.threshold`, `app.lockout.duration`), stored on the Account in the existing `failed_login_attempts` / `locked_until` columns. A Locked Account is refused even with the correct password, with the identical `authentication_failed` body. The lock lifts on its own; a successful login resets the counter. The Account row is locked with `PESSIMISTIC_WRITE` during the login decision, and the counter change commits even when login fails. A new in-memory, keyed `RateLimiter` (GCRA, injected `Clock`, test-only `resetAll()`) limits login to 10 per minute per username, case ignored (`app.rate-limit.login.capacity` / `.period`). A breach returns 429 with `Retry-After`, `code` `too_many_requests` and `detail` "too many requests". A new `EmailService` stub (`LoggingEmailService`) has one operation, `notifyAccountLocked`, which writes only to the `email` logger and file (`app.logging.email-file`) with the recipient masked. Tests swap in `RecordingEmailService`. Lockout (`account_locked`, with the Account UUID) and rate-limit breach (`rate_limited`, with `url.path` and method) write WARN `access-control` audit events. The startup log reports the lockout and rate-limit settings.

**Deviations and decisions:** The SPA's "try again later" on 429 already existed on the login and register pages, the only endpoints that can return 429 today, so the frontend is unchanged and no single global 429 handler was added. Wrong passwords during a lock are counted but do not extend it; the count restarts after the lock expires. Refused attempts use no rate-limit capacity, and `Retry-After` is rounded up to whole seconds. The application-log roll size is now configurable (`app.logging.application-max-file-size`: 10MB by default, 1GB in tests) to fix a `LogCapture` flake caused by a mid-test rollover.

**Known gaps (reviewer notes, non-blocking):** (1) The lock email is sent inside the transaction, before it commits; a real mailer should send after commit. (2) The limiter is keyed on usernames the attacker chooses, so memory and purge cost grow with request volume until ticket 07's IP Throttle caps it; the purge path above 10k keys is untested. (3) `SELECT … FOR UPDATE` on a missing username takes a gap lock on MySQL, which may contend with registration; only H2 is tested (deferred per the non-H2 decision). (4) A 429 login does not end the Session the request carried, unlike a 401. (5) A Disabled Account can still be counted and Locked; no Disabled-Account test until tickets 10/11 can disable one.

**Verification steps:** `./mvnw -o verify` passed: 150 tests, 0 failures, coverage gate met. Frontend: 64 tests passed. Reviewer loop clean on the first pass (Must-fix 0, Human decisions 0), with LockoutApiTest 7/7, LoginRateLimitApiTest 4/4, EmailServiceStubTest 1/1, SessionRestartTest 2/2 and AuditLogTest 5/5 re-run. KB retrieval and code-reviewer compliance gates were skipped by request; mutation testing was skipped.

**Checklist:** all acceptance-criteria boxes ticked.

Commit: `a6e6cf3 feat(auth): Lock Accounts and rate-limit login attempts`
