# Account lockout and IP throttling mechanism

Type: research
Status: resolved
Blocked by: —

## Question

Choose the mechanism for the spec's two-layer brute-force defense:

1. **Account lockout** — `failed_login_attempts` / `locked_until` on the `users` table: N consecutive failures within a window locks the account for a cooldown (defaults e.g. 5 attempts / 15 min).
2. **IP-level throttle** — repeated failures from one IP across multiple usernames triggers throttling **independently** of any account's lockout, so an attacker can't lock out a legitimate user by failing their password (Story 3, third AC).

Sub-questions:

- Enforcement layer for each: servlet filter before authentication, `AuthenticationEventPublisher` listeners, service-level inside the login path, or a mix.
- IP-throttle storage: in-memory bucket (Bucket4j, Caffeine) vs DB. What survives a restart, and does it need to?
- How is the client IP sourced, and is `X-Forwarded-For` trusted (demo with no proxy vs documented deployment assumption)?
- Testability: injectable `Clock` for `locked_until`/window expiry; how tests simulate distinct source IPs.
- Counter reset semantics: does a successful login reset the IP bucket too, or only the account counter?

Investigate against primary sources where applicable. Deliver a findings file plus a recommended design; record it as the answer.

## Answer

Enforce both layers **service-level in the login path**, ordered IP-throttle → account-lock → credentials, so throttled attempts can never move `failed_login_attempts` (the anti-DoS invariant); keep `AuthenticationEventPublisher` events for audit logging only, not state. IP throttle = **in-memory Caffeine cache** keyed by IP with a sliding-window counter (Bucket4j is a fine alternative; DB only needed if clustered — restart loss is acceptable and documented). Source IP = `getRemoteAddr()` only; never trust `X-Forwarded-For` without a stripping proxy (documented deployment assumption; `server.forward-headers-strategy` when proxied). Inject a `Clock` bean for `locked_until`/window expiry; tests use MockMvc `.remoteAddress(...)` (Spring 6.0.10+) for distinct IPs. Success resets the account counter but **not** the IP bucket (window decay only — avoids shared-IP laundering). Flagged deviation: add `last_failed_at` to `users` so the failure window is literal. Full rationale + sources: `../research/03-lockout-throttling-findings.md`.
