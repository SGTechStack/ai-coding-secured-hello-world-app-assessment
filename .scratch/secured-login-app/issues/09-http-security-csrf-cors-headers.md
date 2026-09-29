# 09 — HTTP security: CSRF bootstrap, CORS and headers

Type: grilling
Status: resolved
Blocked by: 01
Map: [Secured Login App](../map.md)
Validated: approved — [validation record](../handoff/validation.md)

## Question

How are CSRF protection, CORS and security headers configured for a cross-origin React SPA using cookie-based sessions?

Answer `Q25` (frontend type), `Q26` (external-domain resources, which drives CSP) and `Q27` (CORS origin allowlist and credential policy), against [`Common_Security_Headers_and_SPA_CSRF_Configuration.md`](../../../App-Standards/Appfw-User-Standards/Shared_Recipes/Common_Security_Headers_and_SPA_CSRF_Configuration.md) and [`Standalone_Session_Login_with_CSRF_Bootstrap.md`](../../../App-Standards/Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Recipes/Standalone_Session_Login_with_CSRF_Bootstrap.md).

The PRD's NFRs make this load-bearing rather than boilerplate: CSRF enabled for **all** state-changing endpoints precisely because auth is cookie-based; an explicit origin allowlist with `Access-Control-Allow-Credentials: true`; `HttpOnly`, `Secure` (prod) and `SameSite` cookie attributes; session-fixation protection (`prd/assessment-prd.md:117`).

The hard part is the **CSRF bootstrap**: a cross-origin SPA cannot read an `HttpOnly` CSRF cookie, and it needs a token before its *first* state-changing call — which includes login and registration, both unauthenticated. Settle how the SPA obtains its first token, which cookie/header names are used, and what `SameSite` value permits the cookie to travel from `localhost:3000` to `localhost:8080` at all. Note the tension to resolve explicitly: `SameSite=Strict` would break the cross-origin flow, while `SameSite=None` requires `Secure`, which local HTTP cannot provide — and local HTTPS is out of scope. State how dev and prod differ, and record the accepted dev gap.

Also decide the security-header set (HSTS, CSP, `X-Content-Type-Options`, frame options) and whether HSTS is configured despite local HTTP.

Blocked on 01 (origins and path shape).

**Amended by [05 — Which logging standards actually bind a two-process app](05-logging-standards-applicability.md).** The security filter chain this ticket defines must accommodate **MDC filters**, and their placement is a chain-ordering decision, not a logging detail — which is why it lands here rather than on [12 — Audit and logging contract](12-audit-and-logging-contract.md).

`Structured_Logging_Application_Standard.md:323` is an `[Enforced Constraint]`: register an MDC filter or interceptor that extracts application-defined fields from each incoming request — a correlation id and **the `user.id` UUID after authentication** — and clears all MDC fields in a `finally` block. 05 established that satisfying it takes **two filters, not one**, and that both placements are chain decisions:

- **`user.id` requires a filter *inside* the Spring Security chain.** `Recipes/Enriching_Logs_With_MDC.md:284` — a request filter registered outside the chain "runs before authentication completes and the user context will not be available". The recipe's placement is `http.addFilterAfter(new MdcUserFilter(), AnonymousAuthenticationFilter.class)` (`MDC:355`), registered **in the `SecurityFilterChain` bean**, not as a `@Component` or via `FilterRegistrationBean` (`MDC:350` warning). It must check the principal type, not just `isAuthenticated()`, because `AnonymousAuthenticationToken.isAuthenticated()` returns `true` (`MDC:330`); `user.id` is simply absent for anonymous requests.
- **Getting a correlation id onto failed logins and 403s requires running *before* Spring Security.** `MDC:75` notes that a `@Component` filter defaults to order `Integer.MAX_VALUE`, i.e. **after** Spring Security's `-100` — so authentication-failure and authorization-denial logs would carry no correlation id. `MDC:79` gives the fix: `@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)`. **This application's entire audit surface is exactly those events** — failed login, lockout, 403 — so the default ordering is not acceptable here.

Settle in this ticket:

- The concrete ordering of both MDC filters relative to `CsrfFilter`, to `AnonymousAuthenticationFilter`, and to **the custom JSON `AuthenticationFilter` that [01 — Context, topology and API surface](01-context-topology-and-api-surface.md) introduced** (registered via `addFilterAt(..., UsernamePasswordAuthenticationFilter.class)`). 01 already flagged that filter as the one security-critical component with no standards recipe behind it; adding two MDC filters around it makes the chain order the thing most likely to be got wrong silently.
- That `finally`-block MDC clearing holds on **every** exit path through this chain, including authentication failure and access denial, which do not return through the normal handler path.
- Do **not** use Logback's built-in `MDCInsertingServletFilter`: `MDC:98` rejects it under this standard because it populates client IP and query strings, both on the must-not-log list (`Structured_Logging_Application_Standard.md:230-231`). Related: whether the client IP is logged at all is [18 — Client IP in logs](18-client-ip-in-logs.md)'s decision, and if 18 permits it, this chain is where it is read — from `getRemoteAddr()`, since there is no gateway to make `X-Forwarded-For` trustworthy.
- Whether this application accepts an inbound `X-Correlation-ID` header from the SPA at all. 05's recommendation is no — untrusted input, no gateway to strip it, and accepting it triggers `Structured_Logging_Application_Standard.md:276`'s CRLF-sanitisation obligation (log forging, CWE-117). [12](12-audit-and-logging-contract.md) owns the final call; this ticket owns whether the header is read in the chain, so coordinate rather than decide twice.

**Amended by [15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md).** **Actuator is now certainly on the classpath**, added solely as Micrometer Tracing's carrier (`Structured_Logging_Application_Standard.md:321` is an `[Enforced Constraint]`; `Recipes/Structured_Logging_Trace_Correlation_And_Context_Propagation.md:12` requires Actuator plus one tracing starter — 15 picked **OpenTelemetry**, for its W3C default). Nothing about Actuator is an offered feature of this application, so `/actuator/**` exposure is a pure attack-surface question and this ticket owns closing it. `management.endpoints.*` sits in the profile-independent `application.yaml` per 15's config layout. 15 also records that **no exporter, endpoint or collector is configured**, so no outbound tracing traffic needs a CORS or CSP allowance.

**Amended by [08 — Authorization matrix](08-authorization-matrix.md).** The matrix is settled, so the four rows that collide with CSRF are now named rather than anticipated — and one conflict this ticket would have inherited turns out not to exist.

- **Four public *and* state-changing rows, all this ticket's to resolve:** `POST ${api.base-path}/auth/login`, `/auth/register`, `/auth/password-reset/request`, `/auth/password-reset/confirm`. 08 fixed them as `permitAll` in the matrix and stopped there; whether each fetches a token from `GET /csrf` first, or is exempted, is entirely 09's. Note 04's dependency on the shape: the reset-confirm row is the escape hatch that lets a flagged user clear `requirePasswordChange`, so exempting or complicating it has a forced-password-change consequence, not just a CSRF one.
- **`/actuator/**` needs no matrix row, and that is 08's positive answer rather than a gap.** `anyRequest().denyAll()` is terminal, so with no row the whole tree is closed to every caller including `USER_MANAGER` — the correct posture for a dependency 15 added purely as tracing's carrier. This ticket therefore owns only `management.endpoints.web.exposure.*`, not an authorization rule. Same treatment for Swagger: `Standalone_Privileged_User_Administration_and_Password_Reset.md:561-562` folds `DEFAULT_SWAGGER_WHITELIST` and `DEFAULT_AUTH_WHITELIST` into the whitelist and **neither is adopted**, since no OpenAPI dependency exists in 15's set.
- **The CSRF conflict this ticket would have inherited does not arise — verified.** 08 set an explicit `authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED))`, which looked like it would turn an anonymous CSRF failure into a `401` and break `Standalone_User_Access_Control_Application_Standard.md:87` and `:445` (both mandate `403`). It does not: `CsrfFilter` in `spring-security-web-7.0.6` holds its **own** `AccessDeniedHandler` field and invokes `handle(...)` directly, never throwing to `ExceptionTranslationFilter`. So CSRF failures are `403` even for anonymous callers, by construction.
- **There is one `AccessDeniedHandler`, not two.** `CsrfConfigurer.getDefaultAccessDeniedHandler` pulls `ExceptionHandlingConfigurer.getAccessDeniedHandler(...)`, wrapping it in a `DelegatingAccessDeniedHandler` / `CompositeAccessDeniedHandler` — so 08's JSON handler serves the CSRF path too. **Do not configure a second one for CSRF**; it would diverge from the authorization path's body for no reason.
- **`GET ${api.base-path}/csrf` is public in the matrix *and* an entry on the forced-change allowlist.** The two are not redundant: the tier-0 filter only ever sees the request when the caller is already authenticated, which is exactly the flagged user's case.
- **Logout's CSRF behaviour is satisfied by construction.** `POST /auth/logout` is an `authenticated` row, so with 08's entry point an expired-session logout returns the `401` that `:438` predicts — the response `:438` explicitly forbids "fixing" by exempting logout from CSRF. No rule needed here beyond not adding one.

## Answer

Resolved by grilling, one round, six questions. Decisions delegated by the user to the orchestrating session after the evidence was presented.

**Session-bound CSRF via the mandated bootstrap endpoint — there is no CSRF cookie at all. `SameSite=Lax` everywhere, `Secure=true` in every profile. Explicit CORS allowlist wired onto the chain. Headers per the recipe minus `preload`, plus `Referrer-Policy`. Actuator exposes `health` only. No inbound correlation header.**

### The ticket's "hard part" does not exist

This ticket was built around a trilemma: a cross-origin SPA cannot read an `HttpOnly` CSRF cookie; `SameSite=Strict` breaks the cross-origin flow; `SameSite=None` needs `Secure`, which local HTTP cannot give. **Both halves are wrong.**

**There is no CSRF cookie to read.** `Standalone_User_Access_Control_Application_Standard.md:238` is an `[Enforced Constraint]`: "The application must strictly use the **Synchronizer Token Pattern** bound to the HTTP Session (e.g., `HttpSessionCsrfTokenRepository`). The **Double Submit Cookie** pattern (e.g., `CookieCsrfTokenRepository`) is **strictly prohibited**, as stateless CSRF cookies weaken the security of our stateful architecture." Both recipes agree (`HDR:56`, `HDR:126-128`, `BOOT:29`) and both make its *absence* an acceptance test — `HDR:157`: "Call `GET /` and verify **no** `XSRF-TOKEN` cookie is present (confirming session storage)"; likewise `BOOT:172`. `withHttpOnlyFalse` and `CookieCsrfTokenRepository` appear nowhere in the tree except in the prohibition. The pattern this ticket worried about being unable to use is the pattern the standard bans.

**And `SameSite` was never in tension.** `SameSite` is evaluated on registrable domain plus scheme — **never on port**. `http://localhost:3000` and `http://localhost:8080` are the *same site*; the flow is cross-**origin** (so CORS binds, and the PRD is right to require it) but same-**site** (so `Lax` does not block it). `SameSite=None` was never needed and `Strict` was never the only alternative. `SameSite=None` in fact appears **nowhere** in `Appfw-User-Standards/`; `lax` is the only value ever named (`Std:350`, `Std:634`, `HDR:73`, `BOOT:40`, `Q:672`).

**The expiry this creates, recorded for 15's deployment assumptions:** `Lax` holds only while the SPA and the API share a registrable domain. `app.example.gov.sg` + `api.example.gov.sg` is fine; putting the SPA on a different domain from the API silently breaks session auth in a way that looks like a cookie bug. Same class of expiry as 18's `X-Forwarded-For` note.

### `Q25` / `Q26` / `Q27` answered

- **`Q25`** — Single-page application; CSRF token endpoint at the **default `/csrf`** (`Q:647`), i.e. `GET ${api.base-path}/csrf`, matching 08's matrix row and `HDR:98`.
- **`Q26`** — **No external-domain resources.** CSP stays `default-src 'self'; object-src 'none';` with an empty whitelist. 15 chose Vite, which bundles locally; 19 needs no CDN; and 15 records no exporter or collector, so tracing adds no outbound origin either.
- **`Q27`** — **CORS enabled, credentials allowed.** Dev origin `http://localhost:3000`; production origins left as an integrator value per `Q:675` ("Never use wildcard or `localhost` origins in production").

### The one-time-token deviation (`Q3`)

`Std:446` — "Previously redeemed CSRF tokens are rejected on subsequent attempts" — **is not satisfiable by the mechanism `Std:238` mandates**, and we do not attempt it.

`HttpSessionCsrfTokenRepository` with `XorCsrfTokenRequestAttributeHandler` (the prescribed default, `HDR:56-57`, `BOOT:29-30`) rotates only the *rendered encoding* per request; the underlying session-bound token is stable for the session's life and is replayable. Nothing in either recipe rotates or redeems it, and no recipe anywhere shows per-request rotation.

**Why not implement it anyway:** an SPA issues concurrent state-changing requests. Two in-flight `PATCH`es against a one-time token means one of them dies on a token the other consumed, with no way for the client to distinguish that from a real CSRF failure. The feature would be an availability bug wearing a security label.

**The reading adopted:** `:446` describes rotation **on authentication**, which we do get — session fixation rotates the session id on login (`BOOT:31`, `Std:338`, `Std:47`), and a session-bound token dies with its session. `Std:89` relies on exactly this: "A second login by the same user invalidates the earlier session. The old session can no longer get a usable CSRF token." That is the standard's own account of when a token stops working, and it is session-scoped, not request-scoped.

**Recorded as a genuine deviation regardless**, unhedged, the same treatment 18 gave its four: under the literal reading of `:446` we do not reject a re-submitted token within a live session.

### Cookies: `Secure=true` in every profile (`Q4`)

`Std:347-350` requires `HttpOnly=true`, `Secure=true`, `SameSite=Lax` on all session and CSRF cookies; both recipes hardcode it with **no profile split** (`HDR:67-73`, `BOOT:40`). The only `@Profile` in either file is the H2-console chain (`HDR:30-40`) — nothing about cookies.

**No dev override.** Browsers treat `http://localhost` as a trustworthy origin and will store and send `Secure` cookies there. **This was not verified in a browser during this session** — it is the one load-bearing claim in this answer that rests on general knowledge rather than a citation or a jar. So:

- **14 must make it an explicit acceptance test**: log in over `http://localhost:8080` from `http://localhost:3000` and assert the session cookie round-trips. It is the first test to run, because everything else depends on it.
- If it fails, the fix is a `dev`-only `server.servlet.session.cookie.secure: false` plus an ADR note. One line, reversible.

Splitting it pre-emptively was rejected because it ships a config whose *dev* profile — the one everyone actually runs — is the non-conformant one, and drift between profiles is how the prod path stops being exercised. The PRD already documents local HTTP as an accepted gap (`prd:121`).

`Std:346` (cookies cleared on logout) and `Std:391` (`Clear-Site-Data`) are **13's**, not this ticket's — flagged there rather than built twice.

### CORS: recipe values, three recipe defects fixed (`Q5`)

The recipe's config block (`HDR:132-151`) is adopted verbatim for its *values*: methods `GET, POST, PUT, PATCH, DELETE, OPTIONS`; headers `Authorization, Content-Type, X-CSRF-TOKEN`; `allowCredentials=true`; `maxAge=3600`. `X-CSRF-TOKEN` in `allowedHeaders` is load-bearing — without it every state-changing call fails preflight and the whole bootstrap is dead.

Three defects in that block, each fixed rather than copied:

1. **The bean is never wired.** Neither `SecurityFilterChain` calls `.cors(...)` — verified across `HDR:82-99` and `BOOT:61-80`. A `CorsConfigurationSource` bean with no `.cors()` on the chain is inert. **Add `.cors(Customizer.withDefaults())`.**
2. **`SecurityProperties props` is undefined** in the recipe (`HDR:138`) and no standard enumerates origins. Replaced with our own `app.security.allowed-origins` list, following 15's externalized-config pattern.
3. **`registerCorsConfiguration("${api.base-path}/**", config)`** (`HDR:148`) puts a property placeholder inside a plain Java string literal with no resolution shown — it would register the *literal* path `${api.base-path}/**` and match nothing. Inject the resolved base path (01 externalized it as `api.base-path`).

Mechanism is the chain's `CorsConfigurationSource`, not `WebMvcConfigurer#addCorsMappings`, per `Std:390`: "CORS policy must be declared through the application's centralized security configuration using an explicit allowlist … Per-endpoint CORS overrides that expand the set of allowed origins are not permitted."

### Headers: recipe minus `preload`, plus `Referrer-Policy` (`Q6`)

Adopted from `HDR:88-96`:

- `Content-Security-Policy: default-src 'self'; object-src 'none';`
- `Permissions-Policy: geolocation=(), microphone=(), camera=()`
- `X-Frame-Options: DENY` and `X-Content-Type-Options: nosniff` — Spring defaults, left alone (`HDR:58-59`)
- `Strict-Transport-Security: max-age=31536000; includeSubDomains`

Three departures:

- **`preload` dropped.** `HDR:95` sets `.preload(true)`; `Q:654`'s stated requirement is `max-age=31536000; includeSubDomains` with no preload, and `Std:389`/`:498`/`:633` never mention it. HSTS preload is a **one-way, domain-wide commitment** submitted to a browser-maintained list, affecting every subdomain of a domain we do not own and cannot un-commit on a normal timescale. Not ours to make on an integrator's behalf. `Q:654` is the binding text.
- **`Referrer-Policy: no-referrer` adopted** although the Standalone standard never names it. It appears in the SSO standard (`SSO_User_Access_Control_Application_Standard.md:429`) as a required header against info leakage; the threat is identical here and the cost is one line. Read as an omission from the Standalone document, not an exclusion. **Recorded as an addition beyond the binding set**, so a reviewer diffing against the standard sees it was deliberate.
- **`Clear-Site-Data` (`Std:391`) is not built here.** Mandated, and absent from `BOOT:74-76`'s logout config — but it is a logout-response header, so it belongs to **13**'s custom `LogoutSuccessHandler`. Flagged, not implemented.

The dev H2-console chain (`HDR:30-40`, `@Order(1)`, `@Profile("dev")`, `frameOptions().sameOrigin()`, `csrf().disable()`) is adopted as-is — 02 put a file-based H2 in `dev`, so the console is genuinely useful, and its `securityMatcher` confines the relaxation to the console path.

### CSRF applies to all four public state-changing rows — no exemptions (`Q5`, continued)

08 fixed `POST /auth/login`, `/auth/register`, `/auth/password-reset/request`, `/auth/password-reset/confirm` as `permitAll`. **All four remain CSRF-protected.** `ignoringRequestMatchers` returns **zero hits across the whole `App-Standards/` tree**; the only CSRF disablement anywhere is the dev H2 chain. `Std:87` is explicit: "Login or any state-changing request without a valid CSRF token is rejected with `403 Forbidden`", and `Std:236`/`:339`/`:402` require it on every data-modifying request.

So the SPA's bootstrap sequence 01 fixed — `GET /csrf` → `POST /auth/login` → `GET /currentUser` — is **mandatory, not stylistic**: without the first call the second returns `403`. Registration and both reset endpoints need the same first call.

**One consequence for 04, which depends on the shape:** the reset-confirm endpoint is the escape hatch that lets a flagged user clear `requirePasswordChange`. It is public, so the tier-0 filter never sees it — but it *is* CSRF-protected, so that user must fetch `/csrf` first. `GET /csrf` is `permitAll` in the matrix **and** on the forced-change allowlist (08), so both paths are open. The hatch holds.

**Logout needs no rule.** `Std:438` forbids exempting it: the `401`/`403` on an expired session is correct behaviour for the SPA's interceptor to absorb. Adopted by not adding anything.

**Two recipe gaps recorded, not fixed here:** the `/csrf` controller at `HDR:110-118` sets no `Cache-Control` although `Std:238` and `Std:443` require the response not be cached — **so add explicit no-store to that endpoint**; and `Std:446`'s redemption clause is the deviation above.

### Actuator: `health` only (`Q7`)

`management.endpoints` appears **nowhere in `App-Standards/`** — no Appfw document prescribes an exposure list. The only rule in this repo is the IM8 skill's: exposure must list exact endpoint ids and a wildcard is a FAIL.

**`management.endpoints.web.exposure.include: health`**, in the profile-independent `application.yaml` per 15's layout. Actuator exists only as Micrometer Tracing's carrier (`Trace:14`) and no endpoint is an offered feature of this application, so the minimum that is still a valid value is right. 08's `denyAll()` already closes `/actuator/**` to every caller with no matrix row — this is defence in depth behind it, and it leaves a liveness probe available to an integrator who later opens one row deliberately.

`/actuator/httpexchanges` is specifically **not** exposed: `Trace:868-878` notes it records the last 100 exchanges in memory and must be restricted. Swagger is moot — no OpenAPI dependency exists in 15's set, so neither `DEFAULT_SWAGGER_WHITELIST` nor `DEFAULT_AUTH_WHITELIST` (`Priv:561-562`) is adopted.

### Filter chain order, and no inbound correlation header (`Q8`)

The concrete order, outermost first:

1. **`CorrelationIdFilter`** — `@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)` per `MDC:79`, so it runs *before* Spring Security's `-100`. Without this, failed logins and `403`s carry no correlation id (`MDC:75`), and **this application's entire audit surface is those events**.
2. **`RateLimitFilter`** (07: per-account and per-IP) — must precede authentication to protect the login endpoint; outside the matrix entirely; writes its own `429` body via 21's `ProblemDetailWriter`.
3. `CsrfFilter` — framework position.
4. **JSON `AuthenticationFilter`** (01) — `addFilterAt(..., UsernamePasswordAuthenticationFilter.class)`.
5. `AnonymousAuthenticationFilter` — framework position.
6. **`MdcUserFilter`** — `addFilterAfter(..., AnonymousAuthenticationFilter.class)` per `MDC:355`, registered **in the `SecurityFilterChain` bean**, never as a `@Component` or `FilterRegistrationBean` (`MDC:350`). Must test the principal *type*, not `isAuthenticated()`, because `AnonymousAuthenticationToken.isAuthenticated()` returns `true` (`MDC:330`); `user.id` is simply absent for anonymous requests.
7. **`AbsoluteSessionTimeoutFilter`** (13).
8. **`PasswordChangeFilter`** (08 tier 0) — preempts the matrix (`Priv:611`).
9. `AuthorizationFilter`.

**Both MDC filters clear in a `finally` block** (`Std_Logging:323`), and that holds on every exit path through this chain including authentication failure and access denial, which do not return through the normal handler path. `MDCInsertingServletFilter` is **not** used — `MDC:98` rejects it because it populates client IP and query strings, both on the must-not-log list.

**The inbound `X-Correlation-ID` header is not read.** 05 recommended this; the reason is stronger than 05 stated. Accepting it triggers `Std_Logging:276`'s CRLF-sanitisation obligation (log forging, CWE-117) — but worse, with no gateway to strip or validate it, an attacker can **set their correlation id to collide with a victim's**, poisoning the audit trail the whole logging stack exists to protect. Generated server-side, always. **12 ratifies**; this ticket records that the chain does not read it.

### Amends

- **13** — owns `Clear-Site-Data` on logout (`Std:391`, `Std:73`, `Std:223`, `Std:635`), which `BOOT:74-76` omits and which needs a custom `LogoutSuccessHandler` per `Std:74`.
- **12** — ratifies the no-inbound-correlation-header call; the MDC field set is `Std_Logging:323`'s (correlation id, `user.id` after authentication).
- **07** — its filter sits at position 2 above, before `CsrfFilter`, and writes its own body.
- **15** — add the `SameSite=Lax` same-registrable-domain constraint to deployment assumptions, beside the HTTPS gap and 18's `X-Forwarded-For` expiry.
- **14** — the `Secure`-cookie-on-`localhost` test is a prerequisite for every other integration test; plus `HDR:155`'s five-header `curl -I` check, the no-`XSRF-TOKEN`-cookie assertion (`HDR:157`), a CSRF-less `POST /auth/login` expecting `403` (`Std:87`), and a non-allowlisted-origin rejection (`Std:501`).
- **04** — the forced-change escape hatch survives CSRF: reset-confirm is protected, but `GET /csrf` is open on both the matrix and the forced-change allowlist.
