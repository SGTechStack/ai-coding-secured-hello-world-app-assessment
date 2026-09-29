# 07: IP Throttle and registration rate limit

**What to build:** One address spraying passwords across many usernames is blocked for 15 minutes after 20 failures in 15 minutes. That block is independent of any Account's lockout. Bulk registration from one address is slowed to 10 per hour. Throttle events are auditable by a keyed hash of the address, never the address itself. See the spec's stories 12, 31–33, "IP Throttle", "Rate limiters", the audit contract's `source.ip_hash`, and ADR 0001 (single instance, in-memory limiters). Use `CONTEXT.md`'s IP Throttle term.

**Blocked by:** 06

**Status:** resolved

- [x] The IP Throttle keeps an in-memory sliding count of failed logins per direct client address, ignoring forwarded headers. 20 failures within 15 minutes blocks that address for 15 minutes (all configurable). It runs first in the Authentication guard.
- [x] A blocked address gets 429 with `Retry-After` for every login attempt, including for Accounts that aren't Locked. A throttle never locks an Account, and a Locked Account doesn't throttle other addresses.
- [x] Registration is limited to 10 per hour per IP (configurable), returning 429 in the same format.
- [x] Throttle and registration-limit audit events (WARN, `access-control`) carry `source.ip_hash`, an HMAC-SHA-256 of the address with a configured key. No log line contains a client IP address.
- [x] Outside `dev`, the HMAC key comes from the secrets manager, and startup fails if it's missing. `dev` uses a fixed local value.
- [x] The IP Throttle exposes a reset operation used only by tests.
- [x] Tests cover (Clock seam): the throttle engages at 20 failures across different usernames and lifts after 15 minutes; it is independent of Account lockout in both directions; 429 plus `Retry-After` from the throttle and from the registration limit; no client IP in any log line; and startup failure outside `dev` without the key.

## Comments

<!-- reviewer-log -->
### Reviewer log

## Findings

| Finding | Required action | Status |
| --- | --- | --- |
| KB unavailable. The acceptance criterion says the IP Throttle uses the direct client address and ignores forwarded headers. server.forward-headers-strategy is not set. When it is unset, Spring Boot 4.1.1 switches to NATIVE on any detected cloud platform: CloudPlatform.isUsingForwardHeaders() returns true for Kubernetes, Cloud Foundry, Heroku, Azure App Service, Nomad and SAP. Kubernetes is detected from KUBERNETES_SERVICE_HOST/PORT. Tomcat's RemoteIpValve then rewrites getRemoteAddr() from X-Forwarded-For when the request arrives from an internal-range address. That changes the IP Throttle key, the registration limiter key and source.ip_hash, depending on where the app runs. forwardedHeadersAreIgnored uses MockMvc, which never runs Tomcat valves, so it cannot catch this.<br><br><details><summary><strong>Verify in</strong></summary><br><code>application.properties:50-55 (app.ip-throttle.*</code><br><code>server.forward-headers-strategy unset)</code></details> | Set server.forward-headers-strategy=none explicitly in application.properties, next to the existing reverse-proxy comment. Operators who add a trusted proxy then change it on purpose, as the spec's deployment note describes. Add a bootstrap assertion in StartupConfigurationTest that the prod profile resolves server.forward-headers-strategy to none, even when KUBERNETES_SERVICE_HOST/PORT or spring.main.cloud-platform=kubernetes is set. | Resolved |


## Full do-work log

Open locally: `artifacts/do-work/07-ip-throttle-and-registration-rate-limit.html`

Includes reviewer findings and human decisions.

### Verification (2026-09-29)

**Implemented:** A new in-memory `IpThrottle` counts failed logins per direct client address (`getRemoteAddr()`), with a sliding window: 20 failures in 15 minutes block the address for 15 minutes (`app.ip-throttle.threshold` / `.window` / `.block-duration`; zero or negative durations are rejected at startup). `AuthenticationGuard` now runs IP Throttle → per-username rate limit → credential check → Locked/Disabled check → counter update. Every refused credential attempt counts against the address. Throttle refusals never touch the Account, so a throttle cannot lock one and a Locked Account doesn't throttle other addresses. Registration is limited to 10 per hour per address (`app.rate-limit.registration.*`), applied after input validation. Both refusals return 429 with `Retry-After`, `too_many_requests` and "too many requests". `server.forward-headers-strategy=none` is set explicitly so forwarded headers are ignored even on a detected cloud platform such as Kubernetes. Refusals write WARN `access-control` audit events (`ip_throttled` / `rate_limited`) with `source.ip_hash`, an HMAC-SHA-256 of the address (`SourceIpHash`); no log line contains the raw address. The key `app.ip-hash.key` (`APP_IPHASH_KEY`) has no default, so startup fails outside `dev` without it; `dev` and the test profile use fixed values. `IpThrottle.reset()` is called only by the per-test `ResetRateLimitersListener`. The startup log reports the throttle and registration-limit settings.

**Deviations and decisions:** Audit events are written for each refused attempt (each 429), not when a block starts. Registrations rejected with 400 for bad input don't use up the registration limit. `LoginRateLimitApiTest.attemptsAreAllowedAgainOnceRetryAfterHasPassed` now sends each attempt from a different address so it still tests only the per-username limit. The CORS startup tests now supply an HMAC key. There is no minimum-length check on the HMAC key, because Spring Boot's bind-failure report would print the rejected value into the log.

**Known gaps (reviewer notes, non-blocking):** (1) `startupFailsWhenTheIpThrottleWindowIsNotPositive` only checks that the failure mentions `app.ip-throttle`, not the `window` field. (2) Above 10k tracked addresses, the cleanup in `IpThrottle.recordFailure` can in rare cases drop an address's failure count.

**Verification steps:** `./mvnw -o verify` passed: 167 tests, 0 failures, coverage gate met. The reviewer loop took one correction round (a must-fix: forwarded headers were trusted on detected cloud platforms, fixed with `server.forward-headers-strategy=none` and a startup test that failed without it) and then passed (Must-fix 0, Human decisions 0). KB retrieval and code-reviewer compliance gates were skipped by request; mutation testing was skipped.

**Checklist:** all acceptance-criteria boxes ticked.

Commit: `52509f8 feat(auth): Throttle failed logins per IP and limit registration`
