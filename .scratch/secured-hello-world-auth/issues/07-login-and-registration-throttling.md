# 07: Login and registration throttling

**What to build:** Password spraying from one source, hammering one username, and bulk account probing through registration are all slowed down. A Throttled request gets a 429 that tells the SPA when to retry, and the SPA shows "try again in N seconds". Throttling is a property of the request, independent of any Account's lock state, and reveals nothing about whether an Account exists. This ticket adds the throttling half of the Login protection module (Bucket4j in memory, single instance only; see ADR-0003).

**Blocked by:** 06 (Account lockout)

**Status:** ready-for-agent

- [ ] Failed logins from one IP are throttled across all usernames. The IP throttle kicks in regardless of whether any Account is Locked.
- [ ] Login attempts are throttled at 10 per minute per submitted username, keyed on the string as typed. Unknown and real usernames get an identical 429.
- [ ] Registration is throttled per IP.
- [ ] Every Throttled response is a 429 `too many requests` Problem Details body with a `Retry-After` header.
- [ ] The client IP comes from the connection's remote address. A forged `X-Forwarded-For` is ignored unless trusted proxies are configured.
- [ ] Every throttle limit and the trusted-proxy list are configuration properties, and tests lower them where that helps.
- [ ] Throttled requests are audited at WARN with the hashed IP only.
- [ ] The SPA turns a 429 into "try again in N seconds", using `Retry-After`.

## Comments

**2026-09-29 — per-username key.** The ticket and ADR-0003 say the key is "the string as typed". The implementation **lowercases** the submitted string and cuts it to 64 characters. It is still never a looked-up Account, so unknown and real usernames get the same 429. Lowercasing matches login, which ignores case. Without it, `Alice`, `aLice` and so on would each get their own 10 attempts a minute against the same Account. The 64-character cut stops an attacker from growing memory with long keys; no username is longer than 32.

**Throttle state in tests.** The buckets are in memory and outlive each test, so `src/test/resources/config/application.yml` gives the shared test context limits it never reaches. `ThrottlingTest` sets real, low limits and gives every test its own addresses and usernames.

**Known gap: HSTS behind a TLS-terminating proxy.** `app.api.trusted-proxies` is used only to work out the client IP. `X-Forwarded-Proto` isn't honoured (`server.forward-headers-strategy: none`), so behind a TLS-terminating proxy `request.isSecure()` is false. HSTS and `Clear-Site-Data` would then not be sent. Hosting is out of scope, but whoever deploys this must trust `X-Forwarded-Proto` from the same proxies.
