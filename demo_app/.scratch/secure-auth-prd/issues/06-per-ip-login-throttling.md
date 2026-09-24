# 06: Per-IP throttling on login

**What to build:** Anyone flooding login from one IP (for example, spraying passwords across many usernames) gets throttled with a `429` and a clear "Too many attempts" message. The real account owners are not locked out. The limiter is reusable, so registration (07) and reset request (09) can apply their own limits. See spec §Backend modules › IP throttle.

**Blocked by:** 01, 02, 03

**Status:** ready-for-agent

- [ ] The limiter is in-memory and single-instance, keyed by `request.getRemoteAddr()`, driven by the `Clock`, and bounded in size (oldest keys evicted) so it can't be used to exhaust memory. The app never parses `X-Forwarded-For` itself.
- [ ] Limits are configuration properties under `app.security.throttle.*`. Login allows 20 **failed** attempts per IP per 15 minutes.
- [ ] While an IP is throttled, login answers `429` with `code: "TOO_MANY_REQUESTS"`, message `"Too many attempts. Please try again later."` and a `Retry-After` header in seconds. This happens before credentials are checked, even when they are correct.
- [ ] Throttling doesn't touch account state: the targeted account is not locked.
- [ ] Another IP is unaffected. The window resets when the clock moves past it.
- [ ] A `LOGIN_THROTTLED` audit event is emitted.
- [ ] The login page shows "Too many attempts. Please try again later." for a `throttled` error, covered by a frontend test.
- [ ] API-seam tests use the controllable clock and distinct remote addresses. The IP throttle is not exercised in e2e, because every e2e test shares one IP.
