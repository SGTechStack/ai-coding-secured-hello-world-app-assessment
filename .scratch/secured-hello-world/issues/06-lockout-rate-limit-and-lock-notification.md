# 06: Account lockout, per-username rate limit, and lock notification

**What to build:** After 5 consecutive wrong passwords, an Account becomes Locked for 20 minutes. Even the right password is refused, with the same generic error, and the Account holder is notified. More than 10 attempts per minute against one username get 429 with a "retry after" signal, and the SPA says "try again later". This ticket introduces the `EmailService` stub (with only the lock notification; tickets 08 and 09 add their own operations) and the rate-limiter infrastructure that later tickets reuse. See the spec's stories 25–30, 33–34 and 105, "Authentication guard", "Rate limiters", and "Notifications (`EmailService` stub)". Use `CONTEXT.md`'s Locked vs Disabled.

**Blocked by:** 04

**Status:** ready-for-agent

- [ ] The failure counter increments on every wrong password. At 5 consecutive failures, `locked_until` is set 20 minutes ahead (configurable). Both are stored on the Account, so they survive a restart.
- [ ] A Locked Account rejects even the correct password with the identical `authentication_failed` body. The lock lifts automatically when it expires, and a successful login resets the counter.
- [ ] The Authentication guard runs in order: per-username rate limit → credential check → Locked/Disabled check → counter update. The IP Throttle slots in ahead of the rate limit in ticket 07.
- [ ] Rate limiters are in memory with keyed buckets, and expose a reset operation used only by tests. Login is limited to 10 per minute per username (configurable). When exceeded: 429 with `Retry-After`, `code` `too_many_requests` and `detail` "too many requests".
- [ ] The `EmailService` stub has one operation, "Account Locked", sent when an Account becomes Locked. It writes to its own logger and file, never the application or audit log, with the recipient masked.
- [ ] Tests can swap in a recording `EmailService`.
- [ ] Audit events: lockout (WARN, `access-control`, with the Account's UUID) and rate-limit breach (WARN, `access-control`, with the endpoint).
- [ ] The SPA shows a "try again later" message on any 429.
- [ ] Tests cover (Clock seam): 5 failures lock; a Locked Account rejects the correct password with the identical body; logging in after expiry succeeds and resets the counter; lockout persists across a context restart; 429 with `Retry-After`, `too_many_requests` and "too many requests"; the lock notification is recorded; and the audit events.
