# 04: Progressive backoff and IP throttle

**What to build:** Repeated failed sign-ins slow the attacker down without ever permanently locking a victim out. After a threshold of consecutive failures on one Account, each further failure sets a delay that doubles from a base up to a cap; a correct password during the delay is still refused, and after it succeeds and resets. Separately, an in-memory per-IP throttle limits repeated failures from one address across many usernames. The sign-in page tells the user when a delay applies.

**Blocked by:** 03 Sign in, greeting, sign out

**Status:** ready-for-agent

- [ ] Threshold (default 3), base delay (default 1 s) and cap (default 15 min) are properties
- [ ] Delay doubles per failure up to the cap and is stored as the Account's delay expiry
- [ ] Correct password during the delay is refused; after the delay it succeeds and resets the counter
- [ ] Per-IP throttle engages independently of any Account's state, held in memory
- [ ] Forwarded-for header is honored only when the forward-headers strategy is configured for a known proxy
- [ ] Audit line when a delay is triggered
- [ ] Integration tests use the controllable clock and cover: growth and cap, recovery, refuse-during-delay, IP throttle independent of Account
- [ ] SPA shows a delay message; frontend tests cover it
- [ ] Backend and frontend verify pass
