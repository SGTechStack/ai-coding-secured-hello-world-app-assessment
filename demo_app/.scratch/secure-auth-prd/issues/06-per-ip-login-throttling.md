# 06: Per-IP throttling on login

**What to build:** Anyone flooding login from one IP (for example, spraying passwords across many usernames) gets throttled with a `429` and a clear "Too many attempts" message. The real account owners are not locked out. The limiter is reusable, so registration (07) and reset request (09) can apply their own limits. See spec §Backend modules › IP throttle.

**Blocked by:** 01, 02, 03

**Status:** resolved

- [x] The limiter is in-memory and single-instance, keyed by `request.getRemoteAddr()`, driven by the `Clock`, and bounded in size (oldest keys evicted) so it can't be used to exhaust memory. The app never parses `X-Forwarded-For` itself.
- [x] Limits are configuration properties under `app.security.throttle.*`. Login allows 20 **failed** attempts per IP per 15 minutes.
- [x] While an IP is throttled, login answers `429` with `code: "TOO_MANY_REQUESTS"`, message `"Too many attempts. Please try again later."` and a `Retry-After` header in seconds. This happens before credentials are checked, even when they are correct.
- [x] Throttling doesn't touch account state: the targeted account is not locked.
- [x] Another IP is unaffected. The window resets when the clock moves past it.
- [x] A `LOGIN_THROTTLED` audit event is emitted.
- [x] The login page shows "Too many attempts. Please try again later." for a `throttled` error, covered by a frontend test.
- [x] API-seam tests use the controllable clock and distinct remote addresses. The IP throttle is not exercised in e2e, because every e2e test shares one IP.

## Comments

- **Limiter.** `security.IpThrottle` is a fixed window per IP: it opens at the first counted attempt and ends `window` later on the injected `Clock`. The key is always `request.getRemoteAddr()`, and a unit test proves `X-Forwarded-For` is ignored. Each instance keeps an insertion-ordered `LinkedHashMap`, capped at `app.security.throttle.max-tracked-ips` (100000). When it is full, the address whose window started first is dropped. An expired window is dropped when its address is next seen. Methods are `synchronized`. Login does check-then-authenticate-then-record, so a burst of parallel failures can overshoot the limit by the number in flight. That is accepted for a single-instance limiter.
- **API for later tickets.** `check(request)` throws `web.TooManyRequestsException(retryAfter)` without counting. `recordAttempt(request)` counts one attempt. `acquire(request)` checks and then counts, for registration (07) and reset request (09), where every request counts. A refused request never counts. `ApiExceptionHandler` turns the exception into `429 TOO_MANY_REQUESTS` with `"Too many attempts. Please try again later."` and `Retry-After` in whole seconds, rounded up, minimum 1.
- **Adding a limit.** Add a `Limit` component to the `ThrottleProperties` record (e.g. `registration`) with a `requirePresent` check, its values under `app.security.throttle.<name>` in `application.yml` (`max-attempts`, `window`), and an `@Bean IpThrottle <name>Throttle` in `ThrottleConfig`. Inject it with `@Qualifier("<name>Throttle")`. Audit the refusal at the call site (`REQUEST_THROTTLED` for non-login endpoints) by catching `TooManyRequestsException` and rethrowing it, the same way `AuthController` records `LOGIN_THROTTLED`. A missing or non-positive limit stops startup.
- **Login.** `AuthController.login` checks the throttle after body validation and before `authenticate`, so a throttled request never touches account state and never logs `LOGIN_FAILURE`. Only `AuthenticationException`s count. Blank-field `400`s don't count, and neither do successes.
- **CORS.** `Retry-After` is in `CorsConfiguration.setExposedHeaders`. The SPA shows the fixed message for kind `throttled` and doesn't read the header yet.
- **Tests.** `LoginThrottleApiTest` imports `TestClockConfig`, resets the clock in `@BeforeEach` and uses a distinct `198.51.100.x` address per test through `SpaAuthFlow.fromIp(ip)`, a public `RequestPostProcessor`. The throttle is a singleton in the cached context, so its counters outlive a test class. Every MockMvc request is otherwise `127.0.0.1`. Any test that makes failed logins in bulk (lockout, 08) must send them `.with(fromIp(...))` from its own address, or it will throttle later tests in the same context. Moving a `MutableClock` past 15 minutes also ends every window in that context. There is deliberately no reset method on the limiter.
- **E2E.** The IP throttle is not exercised in e2e. The Playwright config wasn't touched, because ticket 04 owns it right now. Each e2e run adds a few failed logins from one IP. With `reuseExistingServer` locally, many reruns within 15 minutes could hit the 20 limit. If 07/08 add more failing flows, pass `APP_SECURITY_THROTTLE_LOGIN_MAX_ATTEMPTS` (and the registration and reset equivalents) to the e2e backend `webServer` env. The API-seam tests stay authoritative for the default limits.
