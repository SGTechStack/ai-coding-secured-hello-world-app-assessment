# 07: IP Throttle and registration rate limit

**What to build:** One address spraying passwords across many usernames is blocked for 15 minutes after 20 failures in 15 minutes. That block is independent of any Account's lockout. Bulk registration from one address is slowed to 10 per hour. Throttle events are auditable by a keyed hash of the address, never the address itself. See the spec's stories 12, 31–33, "IP Throttle", "Rate limiters", the audit contract's `source.ip_hash`, and ADR 0001 (single instance, in-memory limiters). Use `CONTEXT.md`'s IP Throttle term.

**Blocked by:** 06

**Status:** ready-for-agent

- [ ] The IP Throttle keeps an in-memory sliding count of failed logins per direct client address, ignoring forwarded headers. 20 failures within 15 minutes blocks that address for 15 minutes (all configurable). It runs first in the Authentication guard.
- [ ] A blocked address gets 429 with `Retry-After` for every login attempt, including for Accounts that aren't Locked. A throttle never locks an Account, and a Locked Account doesn't throttle other addresses.
- [ ] Registration is limited to 10 per hour per IP (configurable), returning 429 in the same format.
- [ ] Throttle and registration-limit audit events (WARN, `access-control`) carry `source.ip_hash`, an HMAC-SHA-256 of the address with a configured key. No log line contains a client IP address.
- [ ] Outside `dev`, the HMAC key comes from the secrets manager, and startup fails if it's missing. `dev` uses a fixed local value.
- [ ] The IP Throttle exposes a reset operation used only by tests.
- [ ] Tests cover (Clock seam): the throttle engages at 20 failures across different usernames and lifts after 15 minutes; it is independent of Account lockout in both directions; 429 plus `Retry-After` from the throttle and from the registration limit; no client IP in any log line; and startup failure outside `dev` without the key.
