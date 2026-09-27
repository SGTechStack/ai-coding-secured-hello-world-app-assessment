# Hello Auth security and configuration introduction

This guide explains how Hello Auth's React frontend and Spring Boot backend protect logins, cookies and requests. Read it before you change the security configuration, add a route or prepare a deployment.

![Senior developer in mustard and junior developer in teal reviewing security together](artifacts/introduction/developers.png)

**Status:** Draft · **Owner:** [Authoring placeholder: confirm before publication] · **Last reviewed:** 27 September 2026 · **Revision:** `01efc8ed1560e409ba66a89ff835391ac6c4e101`

**Scope:** This is a guided introduction, not a full audit. The review covered the current working tree, including uncommitted changes, on top of the recorded HEAD revision. It starts from SecurityConfig.java and application-prod.yml, then follows the code they drive. Official standards and guidance were checked online. No production system was exercised. The standards mappings are assessments, not certifications.

**How to read each panel:** A junior developer asks a question and a senior developer answers it. Diagrams show the flow, and the bullets add detail. Each bullet starts with a label. These labels show how certain a statement is:

- **In the code:** confirmed by reading the source.
- **Deployment:** depends on how the app is run.
- **Not known:** these files cannot answer it.
- **Not verified:** documented framework behaviour that still needs a test.
- **Watch out:** behaviour that can surprise you.

Other labels, such as "Logout" or "Limits", name the topic of the bullet.

Each panel ends with what to preserve when you change the code, and links to the relevant source lines.

## 01 · Start with the big picture

> **JUNIOR DEV:** Can two config files tell us if the app is secure?

**SENIOR DEV:** They show the rules, not the whole story. The React single-page application (SPA) runs in the browser and sends cookies to the Spring Boot API. Spring Security filters every request first. Controllers and services then handle login, throttling, password reset and admin actions. Sessions are stored in the database.

![How a request travels from the browser to the database](artifacts/introduction/img/trust-boundaries.png)

*How a request travels from the browser to the database.* [Open the interactive request-flow diagram (HTML)](artifacts/introduction/diagrams/trust-boundaries.html)

Text description: The SPA sends HTTPS requests with cookies to a TLS proxy, which forwards them to the Spring Boot API. Inside the API, security filters (CORS, CSRF and access rules) run before controllers and services. Controllers count login failures in an in-memory IP throttle and use JDBC to reach the database, which stores users and sessions. The diagram labels the database PostgreSQL or MySQL; the engine in use is not known from these files.

- In the code: SecurityConfig sets up the filters. AuthController saves the login. application-prod.yml overrides the defaults when the prod profile is active.
- Not known: these files do not cover the proxy, database permissions or the headers sent by whatever hosts the frontend. Each needs its own check.

**What to preserve:** Treat the config as a map, not a certificate. A code comment explains intent; it does not prove that the deployed system matches it.

**Source anchors:** [SecurityConfig.java:80](<backend/src/main/java/com/example/helloauth/config/SecurityConfig.java#L80>) · [application-prod.yml:34](<backend/src/main/resources/application-prod.yml#L34>) · [AuthController.java:89](<backend/src/main/java/com/example/helloauth/auth/AuthController.java#L89>) · [api.ts:11](<frontend/src/lib/api.ts#L11>)

## 02 · Config order is not filter order

> **JUNIOR DEV:** Do the HttpSecurity calls run top to bottom, like a pipeline?

**SENIOR DEV:** No. Each call configures a block, and Spring Security decides where each filter goes. Moving cors() above headers() changes nothing at runtime. The order that matters is fixed: Spring Security rejects a bad cross-site request forgery (CSRF) token before your controller runs.

**In short:** Pick the matching filter chain → Run security filters → Check access rules → Controller (only if allowed)

- In the code: the main chain turns on cross-origin resource sharing (CORS), SPA-style CSRF protection, security headers, path rules and logout. A separate dev-only chain for the H2 database console runs first.
- Good to know: only the first chain that matches a request handles it.
- In the code: the CSRF block sets a session-backed token repository and the XOR request handler. Login (which rotates the token) and logout (which clears it) share the same repository bean.

**What to preserve:** Do not switch to csrf.spa(), because it swaps in the cookie-based repository. When you add filters, check the actual filter list, for example with Spring Security debug logging, instead of inferring it from code order.

**Source anchors:** [SecurityConfig.java:80](<backend/src/main/java/com/example/helloauth/config/SecurityConfig.java#L80>) · [SecurityConfig.java:247](<backend/src/main/java/com/example/helloauth/config/SecurityConfig.java#L247>) · [SecurityConfig.java:155](<backend/src/main/java/com/example/helloauth/config/SecurityConfig.java#L155>)

## 03 · Public does not mean unchecked

> **JUNIOR DEV:** If an endpoint is permitAll, what can still reject me?

**SENIOR DEV:** CSRF checks, input validation and service rules still apply. The public list is short: register, login, csrf, me, and everything under /api/auth/password-reset/**. /actuator/health is also public. /api/admin/** needs the ADMIN role, and every other route needs a signed-in user.

- In the code: /api/auth/me is public at the filter level, but the controller returns 401 if you are not signed in.
- Watch out: /api/auth/password-reset/** is a wildcard. Any new endpoint added under that path is public automatically.
- In the code: AdminService adds its own rules. Admins cannot change their own account. Disabling, deleting or demoting a user ends that user's sessions.
- In the code: ERROR dispatches are explicitly permitted. The fallback rule is authenticated(), not denyAll(), so any signed-in user can reach a new route unless you give it a narrower rule.

**What to preserve:** When you add a route, check both the filter rules and the service rules. Never widen the public list to /api/auth/**.

**Source anchors:** [SecurityConfig.java:110](<backend/src/main/java/com/example/helloauth/config/SecurityConfig.java#L110>) · [AuthController.java:138](<backend/src/main/java/com/example/helloauth/auth/AuthController.java#L138>) · [AdminService.java:62](<backend/src/main/java/com/example/helloauth/admin/AdminService.java#L62>) · [PasswordResetController.java:19](<backend/src/main/java/com/example/helloauth/passwordreset/PasswordResetController.java#L19>) · [ApiExceptionHandler.java:86](<backend/src/main/java/com/example/helloauth/auth/ApiExceptionHandler.java#L86>)

## 04 · One cookie, one token in memory

> **JUNIOR DEV:** Where does the CSRF token live, and why not in a cookie?

**SENIOR DEV:** The application's session cookie is SESSION, an opaque identifier that JavaScript cannot read. It identifies anonymous sessions as well as signed-in ones. The CSRF secret stays in the server-side session.

GET /api/auth/csrf returns an XOR-masked copy of that secret. The SPA keeps it in memory and sends it in the X-XSRF-TOKEN header on every change. Spring unmasks the value and compares it with the stored secret. This implements OWASP's Synchronizer Token pattern.

The browser's origin controls stop untrusted origins from reading the token. Cross-site scripting (XSS) or an overly permissive CORS policy can defeat that protection.

![Synchronizer Token pattern: fetched from the session, held in memory, sent in a header](artifacts/introduction/img/csrf-cookies.png)

*Synchronizer Token pattern: fetched from the session, held in memory, sent in a header.* [Open the interactive CSRF token diagram (HTML)](artifacts/introduction/diagrams/csrf-cookies.html)

Text description: First, the SPA calls GET /api/auth/csrf. The CSRF filter saves a random token in the session store and returns it in JSON along with the SESSION cookie. Next, the SPA sends a change with the token in the X-XSRF-TOKEN header. The filter loads the session token and, when the header matches, passes the request to the controller, which returns 200. Finally, a forged request from another website carries the cookie but no token, and the filter returns 403 "Invalid CSRF token".

![What happens to the session and its CSRF token over time](artifacts/introduction/img/session-lifecycle.png)

*What happens to the session and its CSRF token over time.* [Open the interactive session lifecycle diagram (HTML)](artifacts/introduction/diagrams/session-lifecycle.html)

Text description: A visitor starts anonymous, with no session. GET /api/auth/csrf stores a token in a session. POST /api/auth/login creates a new SESSION and token. The user then sends the cookie and token header with every change. POST /api/auth/logout deletes the session row. From the signed-in state, 30 minutes without requests expires the session. A password reset or admin action revokes it on the server.

- In the code: in prod, SESSION is Secure, HttpOnly and SameSite=Strict. Outside prod, the defaults are Secure=false and SameSite=Lax. Sessions expire after 30 minutes without a request.
- Why not a cookie: the naive double-submit pattern puts the token in a cookie. An attacker who can write cookies on your domain, for example from a sibling subdomain, could then plant a known value. A token kept only on the server cannot be planted. HttpOnly does not stop injected script from sending requests, so XSS prevention still matters.
- In the code: an expired session loses its token. A change sent with a stale token gets 403 "Invalid CSRF token", and the SPA fetches a new token and retries once. The retry does not restore authentication, so protected routes can then return 401. GET /api/auth/csrf creates an anonymous session when needed.
- Deployment: SameSite=Strict blocks cross-site cookies even when CORS allows the origin and the fetch includes credentials. Separate HTTPS subdomains can be cross-origin but still same-site. A frontend hosted on a different site will not work with this cookie policy.

**What to preserve:** Keep the token out of cookies, localStorage and URLs. Test HTTPS, SameSite and credentialed CORS together, because the SESSION cookie needs to reach the API in every combination you deploy.

**Source anchors:** [SecurityConfig.java:247](<backend/src/main/java/com/example/helloauth/config/SecurityConfig.java#L247>) · [SecurityConfig.java:288](<backend/src/main/java/com/example/helloauth/config/SecurityConfig.java#L288>) · [application.yml:1](<backend/src/main/resources/application.yml#L1>) · [api.ts:11](<frontend/src/lib/api.ts#L11>)

## 05 · Follow one login

> **JUNIOR DEV:** Who actually creates the signed-in session?

**SENIOR DEV:** AuthController passes the username and password to LoginService, which runs three checks in order: IP throttle, account lock, then password (BCrypt). If all three pass, the controller gives the session a new ID, saves the signed-in user to the session store and returns 200. The SPA then fetches a fresh CSRF token.

![One successful login, step by step](artifacts/introduction/img/login.png)

*One successful login, step by step.* [Open the interactive login diagram (HTML)](artifacts/introduction/diagrams/login.html)

Text description: The SPA posts to /api/auth/login with the CSRF header. AuthController calls LoginService, which confirms the IP has fewer than 4 recent failures, confirms the account is unlocked and verifies the BCrypt password. The controller then saves a new session ID and the user to the session store, and returns 200 with the SESSION cookie. The SPA requests a new token from GET /api/auth/csrf.

![What stops password guessing](artifacts/introduction/img/rate-limit.png)

*What stops password guessing.* [Open the interactive rate-limit diagram (HTML)](artifacts/introduction/diagrams/rate-limit.html)

Text description: A login attempt passes the IP throttle, the account lock and the password check in that order. A throttled IP gets 429, and the password is not checked. A locked account gets the generic 401. A wrong password records a failure against both the IP and the account, returns the same 401, and locks the account for 15 minutes on the fifth failure. Because an IP is blocked after 4 failures, one attacker IP cannot lock an account on its own.

- Why a new session ID: it prevents session fixation, where an attacker plants a session ID before you log in.
- Failure responses: a throttled login returns 429. A wrong password, an unknown user and a locked account all return the same 401. A missing or wrong CSRF token returns 403. A signed-in user without the ADMIN role is denied on admin routes.
- Logout: POST /api/auth/logout deletes the session on the server. The SPA clears its own state even if that call fails, so the server session can survive until it expires.

**What to preserve:** Keep the session ID change before saving the login. Hiding the UI does not log the user out; only the server call ends the session.

**Source anchors:** [AuthController.java:89](<backend/src/main/java/com/example/helloauth/auth/AuthController.java#L89>) · [LoginService.java:80](<backend/src/main/java/com/example/helloauth/auth/LoginService.java#L80>) · [SecurityConfig.java:223](<backend/src/main/java/com/example/helloauth/config/SecurityConfig.java#L223>) · [SecurityConfig.java:201](<backend/src/main/java/com/example/helloauth/config/SecurityConfig.java#L201>) · [SecurityConfig.java:133](<backend/src/main/java/com/example/helloauth/config/SecurityConfig.java#L133>) · [auth-context.tsx:80](<frontend/src/auth/auth-context.tsx#L80>) · [HelloController.java:16](<backend/src/main/java/com/example/helloauth/HelloController.java#L16>) · [ApiExceptionHandler.java:86](<backend/src/main/java/com/example/helloauth/auth/ApiExceptionHandler.java#L86>)

## 06 · Prod is a profile, not a guarantee

> **JUNIOR DEV:** Which settings win when the app starts?

**SENIOR DEV:** application.yml is the base. When the prod profile is active, application-prod.yml overrides it. Environment variables and command-line arguments override both. The frontend works differently: VITE_API_BASE is fixed when you build the SPA, so changing it later on the server has no effect.

**In short:** application.yml → application-prod.yml → Env vars / CLI args → Bind, validate, start

- In the code: prod turns off the H2 console and the embedded database, lets Liquibase manage the schema, and has Hibernate only validate it.
- Watch out: the dev profile switches on the dev-only H2 chain; the absence of prod does not. If someone activates dev and prod together, the dev chain is still present. Pin the profile list in the deployment.
- Not known: these files do not show the actual deployment overrides, the database engine, the Transport Layer Security (TLS) setup and the frontend hosting.

**What to preserve:** Review the effective configuration, including which profiles are active. Keep credentials out of source code, screenshots and logs.

**Source anchors:** [application.yml:1](<backend/src/main/resources/application.yml#L1>) · [application-prod.yml:34](<backend/src/main/resources/application-prod.yml#L34>) · [AppProperties.java:12](<backend/src/main/java/com/example/helloauth/config/AppProperties.java#L12>) · [HelloAuthApplication.java:8](<backend/src/main/java/com/example/helloauth/HelloAuthApplication.java#L8>) · [api.ts:11](<frontend/src/lib/api.ts#L11>) · [SecurityConfig.java:155](<backend/src/main/java/com/example/helloauth/config/SecurityConfig.java#L155>)

## 07 · Missing settings fail in different ways

> **JUNIOR DEV:** Does every missing environment variable stop the app from starting?

**SENIOR DEV:** No. Two validators stop startup on purpose:

- ProdAdminCredentialsValidator rejects a blank admin username or password, or a password shorter than app.password-min-length (12 by default).
- SecurityTunablesValidator rejects an IP failure limit that is not lower than the account lockout limit.

Other settings behave differently when blank instead of stopping startup.

- Database: DB_URL, DB_USERNAME and DB_PASSWORD have no defaults, and no validator checks them. A bad value fails later, when the app tries to connect, and the error depends on which value is wrong.
- Blank but allowed: an empty APP_CORS_ALLOWED_ORIGINS makes the CORS policy reject cross-origin browser requests, but same-origin access still works. It is not a firewall. An empty APP_RESET_LINK_BASE_URL neither stops startup nor turns off password reset.
- Watch out: the seeder creates the admin only if no admin exists yet. Changing APP_ADMIN_PASSWORD later does not change an existing admin's password.

**What to preserve:** Keep three cases distinct: the app refuses to start, a feature does not work, and nothing checks the setting. Consider adding validation for production URLs.

**Source anchors:** [ProdAdminCredentialsValidator.java:25](<backend/src/main/java/com/example/helloauth/config/ProdAdminCredentialsValidator.java#L25>) · [SecurityTunablesValidator.java:22](<backend/src/main/java/com/example/helloauth/config/SecurityTunablesValidator.java#L22>) · [application-prod.yml:34](<backend/src/main/resources/application-prod.yml#L34>) · [AdminSeeder.java:54](<backend/src/main/java/com/example/helloauth/admin/AdminSeeder.java#L54>) · [EmailService.java:39](<backend/src/main/java/com/example/helloauth/passwordreset/EmailService.java#L39>) · [ProdProfileTests.java:29](<backend/src/test/java/com/example/helloauth/ProdProfileTests.java#L29>) · [StartupConfigValidationTests.java:34](<backend/src/test/java/com/example/helloauth/StartupConfigValidationTests.java#L34>)

## 08 · The API trusts its proxy

> **JUNIOR DEV:** Why does a proxy setting matter for login security?

**SENIOR DEV:** In prod, the API trusts the Forwarded and X-Forwarded-* headers. These headers tell it whether the original request used HTTPS and what the client's IP address was. The IP throttle uses that address, and HTTPS-only behaviour such as HTTP Strict Transport Security (HSTS) depends on the scheme. The proxy therefore needs to remove any of these headers sent by the client and write the real values.

![Why the API depends on a trusted proxy](artifacts/introduction/img/proxy-trust.png)

*Why the API depends on a trusted proxy.* [Open the interactive proxy trust diagram (HTML)](artifacts/introduction/diagrams/proxy-trust.html)

Text description: A real user and an attacker who sends a fake X-Forwarded-For header both reach the trusted proxy. The proxy terminates HTTPS, drops client-sent forwarding headers, writes the true client IP and scheme, and forwards a clean request to the Spring Boot API. The API passes getRemoteAddr() to the IP throttle and isSecure() to HSTS and Secure cookies. Only the proxy can reach the API. Without this, an attacker can use a new fake IP for each request and never hit the throttle. Alternatively, every user shares the proxy's IP and gets throttled together.

- Deployment: only the proxy should be able to reach the API. The YAML setting does not check where the headers came from.
- In the code: by default, an IP is throttled after 4 failures, and an account is locked for 15 minutes after 5 failures. Counters reset after a gap of more than 10 minutes between recorded failures; this is not a precise rolling window. Register and reset requests share a separate budget of 20 per IP, with the same reset-after-gap behaviour.
- Limits: each server keeps IP counts in memory. A restart or a second replica resets them, and a known race condition affects concurrent requests. Do not treat the 4 < 5 rule as a cluster-wide guarantee.

**What to preserve:** Treat proxy trust and rate-limit storage as deployment decisions. Test with multiple instances before you promise limits across the whole system.

**Source anchors:** [application-prod.yml:34](<backend/src/main/resources/application-prod.yml#L34>) · [IpThrottleService.java:54](<backend/src/main/java/com/example/helloauth/auth/IpThrottleService.java#L54>) · [LoginService.java:80](<backend/src/main/java/com/example/helloauth/auth/LoginService.java#L80>) · [SecurityTunablesValidator.java:22](<backend/src/main/java/com/example/helloauth/config/SecurityTunablesValidator.java#L22>) · [application.yml:1](<backend/src/main/resources/application.yml#L1>)

## 09 · Headers only protect their own response

> **JUNIOR DEV:** Does the API's Referrer-Policy protect the frontend reset page?

**SENIOR DEV:** No. The API sends a strict Content Security Policy (CSP), a referrer policy and a permissions policy, but they apply only to the API's own responses. The React app is a separate page, and whatever serves it sets its headers. That host needs its own policy, especially for the reset page, because the reset page URL contains the token.

- In the code: the API CSP is default-src 'none'; frame-ancestors 'none'. This is a good defence for a JSON API, but it does not replace XSS protection in the frontend.
- Good to know: strict-origin-when-cross-origin hides the path and query from other sites. Same-origin requests still see the full URL, including the token.

**What to preserve:** Check API headers, frontend page headers and proxy behaviour separately.

**Source anchors:** [SecurityConfig.java:103](<backend/src/main/java/com/example/helloauth/config/SecurityConfig.java#L103>) · [EmailService.java:39](<backend/src/main/java/com/example/helloauth/passwordreset/EmailService.java#L39>)

## 10 · "Link sent" does not mean an email was sent

> **JUNIOR DEV:** The page says a reset link was sent. Is that true in prod?

**SENIOR DEV:** Not yet. The reply is the same whether or not the email address exists, which makes it harder to discover accounts from the response text. Equal response timing has not been established. EmailService is a stub that writes to the log, and in prod it does not log the link. The app has no real email sender yet.

![Password reset: request a link, then confirm](artifacts/introduction/img/password-reset.png)

*Password reset: request a link, then confirm.* [Open the interactive password reset diagram (HTML)](artifacts/introduction/diagrams/password-reset.html)

Text description: To request a link, the SPA posts an email address. The reset controller checks the IP budget, and the reset service saves a SHA-256 hash of the token with a 15-minute expiry. The service then asks the email stub to send the link, and the controller returns the same reply for any email address. To confirm, the SPA posts the token and new password. The service checks the token and password length, uses the token once, saves the new BCrypt hash and deletes the user's sessions. The controller returns 200, or a generic 400.

- In the code: the token uses 32 random bytes, only its SHA-256 hash is stored, and it expires after 15 minutes. On confirm, the service validates the token and password length, atomically claims the unused token, saves the BCrypt hash and deletes the user's sessions.
- Watch out: prod hides the token, but the stub still logs the recipient's email address. A log without tokens can still contain personal data.
- Not verified: @Transactional covers the reset service, but Spring Session JDBC documents REQUIRES_NEW for session operations, and no transaction override was found. Sharing a database therefore does not make the password and token changes atomic with the session deletions. Rollback behaviour needs testing; see the session persistence row in the standards table.

**What to preserve:** Never enable token logging to work around the missing email sender. Keep token consumption and the password update transactional, keep session invalidation, and verify how its separate transaction behaves.

**Source anchors:** [EmailService.java:39](<backend/src/main/java/com/example/helloauth/passwordreset/EmailService.java#L39>) · [PasswordResetService.java:109](<backend/src/main/java/com/example/helloauth/passwordreset/PasswordResetService.java#L109>) · [PasswordResetController.java:19](<backend/src/main/java/com/example/helloauth/passwordreset/PasswordResetController.java#L19>) · [application-prod.yml:34](<backend/src/main/resources/application-prod.yml#L34>)

## Standards and guidance implemented

Official sources were checked online on 27 September 2026 and compared with the current working tree. OWASP cheat sheets are recommendations, RFCs and web specifications define protocols, and Spring documentation describes framework behaviour. A mapping in this table does not establish full compliance or production security. Values such as 12 characters, 30 minutes and 4/5 failures are choices made by this application.

| Control | Standard or guidance | Implementation and assessment |
| --- | --- | --- |
| CSRF | [OWASP Synchronizer Token pattern](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#synchronizer-token-pattern) | `SecurityConfig.csrfTokenRepository()` uses `HttpSessionCsrfTokenRepository`. `/api/auth/csrf` returns a token representation, and `api.ts` holds it in memory and sends `X-XSRF-TOKEN`. This implements the stateful synchronizer pattern. OWASP also recognises signed double-submit cookies, so not every cookie-based alternative has the naive pattern's weakness. |
| Token masking and lifecycle | [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) | `XorCsrfTokenRequestAttributeHandler` masks exposed tokens to reduce BREACH-style compression leakage, then unmasks submitted values. Masking is not encryption, and it does not rotate the stored secret per request. Authentication clears the secret, the bootstrap call obtains a new one, and logout clears it. |
| Credentialed CORS | [WHATWG Fetch: CORS protocol](https://fetch.spec.whatwg.org/#http-cors-protocol), a Living Standard | `corsConfigurationSource()` configures explicit origins, methods and headers with `allowCredentials=true`. `api.ts` defaults to `credentials: include`. Credentialed responses require an explicit origin, not `*`. CORS does not authenticate callers, act as a firewall or override `SameSite`. |
| Cookie attributes | [RFC 6265: Secure and HttpOnly](https://www.rfc-editor.org/rfc/rfc6265.html#section-4.1.2) and [RFC 6265bis cookie revision draft: SameSite](https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis) | Production sets `Secure`, `HttpOnly` and `SameSite=Strict`, and `sessionCookieCustomizer()` applies them to Spring Session. SameSite is defined in the revision draft, not the original RFC. Cross-origin HTTPS subdomains can be same-site. An SPA on a different site cannot use this Strict session cookie, even if CORS allows it. |
| Session fixation and expiry | [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html) | `AuthController` invokes `ChangeSessionIdAuthenticationStrategy` before saving authentication. Logout invalidates the session, and `application.yml` sets a 30-minute idle expiry. This implements ID renewal and server-side expiry and invalidation. No absolute session lifetime is configured, and 30 minutes is not a universal OWASP requirement. |
| Authorisation | [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): least privilege and checks on every request | Explicit public routes, `hasRole("ADMIN")` and the `AdminService` self-action guards implement role-based access and service restrictions. Alignment with deny-by-default guidance is partial: `authenticated()` lets any signed-in user reach new routes unless narrowed, `password-reset/**` is public, and ERROR dispatches are permitted. |
| Password storage | [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#bcrypt) and [Spring BCryptPasswordEncoder](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoder.html) | `new BCryptPasswordEncoder()` uses Spring's documented default cost of 10, which meets OWASP's minimum bcrypt cost. OWASP prefers Argon2id for new systems and reserves bcrypt for legacy use. Registration and reset data transfer objects (DTOs) permit 128 characters and do not enforce bcrypt's 72-byte limit. Verify oversized-input behaviour against the resolved library version. |
| Password policy and guessing resistance | [OWASP Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html) | The default minimum length of 12, IP threshold of 4, account threshold of 5 and 15-minute lockout are local choices. `LoginService` runs the throttle, account and credential checks in order, and the controller returns generic credential failures. Alignment is partial: current guidance considers passwords under 15 characters weak without multi-factor authentication (MFA), and this login has no MFA. Per-process IP state, concurrency and unverified response timing limit these guarantees. |
| Password recovery | [OWASP Forgot Password](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html) | `PasswordResetService` uses 32 random bytes, hashed storage, a 15-minute expiry and atomic single-use consumption. The controller returns a generic message and applies an IP budget. The core token controls are present, but real email delivery is missing and uniform response timing has not been established. The reset page needs its own referrer policy to prevent token leakage. |
| Credential-safe logging | [OWASP Logging: data to exclude](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html#data-to-exclude) | `log-reset-link=false` keeps the reset credential out of production logs. `EmailService` still logs the email address. This aligns with excluding credentials on this path; it is not a complete logging or privacy assessment. |
| CSP and framing | [W3C CSP Level 3](https://www.w3.org/TR/CSP3/) (Working Draft) and [RFC 7034: X-Frame-Options](https://www.rfc-editor.org/rfc/rfc7034.html) (Informational) | The main chain sets `default-src 'none'; frame-ancestors 'none'` and keeps Spring's default `X-Frame-Options: DENY`. `frame-ancestors` needs its own directive because it does not inherit from `default-src`. The dev H2 chain uses SAMEORIGIN. The API policy does not protect a separately hosted SPA document. |
| Referrer disclosure | [W3C Referrer Policy](https://www.w3.org/TR/referrer-policy/#referrer-policy-strict-origin-when-cross-origin) | `strict-origin-when-cross-origin` limits cross-origin referrers to the origin and suppresses referrers on HTTPS-to-HTTP downgrades. It can keep the path and query for same-origin requests. This API header does not set the reset page's policy. |
| Browser features | [W3C Permissions Policy](https://www.w3.org/TR/permissions-policy/) (draft) | `camera=(), microphone=(), geolocation=()` uses empty allow-lists to disable these features in applicable document contexts. Browser support varies. Headers on JSON API responses do not configure the separate frontend document. |
| HSTS | [RFC 6797](https://www.rfc-editor.org/rfc/rfc6797.html) and [Spring security headers](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html) | The main chain keeps Spring's default HSTS writer for secure requests, and production header forwarding affects the detected scheme. HSTS tells browsers to use HTTPS after a secure first contact. This configuration does not terminate TLS or enrol the site for preload. Proxy trust and the actual headers need verification in the deployment. |
| Caching and MIME sniffing | [RFC 9111: no-store](https://www.rfc-editor.org/rfc/rfc9111.html#section-5.2.2.5), [WHATWG Fetch: nosniff](https://fetch.spec.whatwg.org/#x-content-type-options-header), [Spring default headers](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html) | The main chain keeps the default cache restrictions and `X-Content-Type-Options: nosniff` unless overridden. Spring's `X-XSS-Protection: 0` disables a legacy browser filter; it is not an XSS prevention standard. Response headers were not measured. |
| Proxy metadata | [RFC 7239: Forwarded](https://www.rfc-editor.org/rfc/rfc7239.html) and [Spring forwarded-header handling](https://docs.spring.io/spring-framework/reference/web/webmvc/filters.html#filters-forwarded-headers) | `forward-headers-strategy=framework` processes `Forwarded` and the conventional `X-Forwarded-*` headers, and throttling uses `getRemoteAddr()`. RFC 7239 does not standardise `X-Forwarded-*` or authenticate these claims. For the values to be reliable, a trusted boundary proxy needs to strip incoming values, and direct access to the backend needs to be blocked. |
| Structured errors | [RFC 9457: Problem Details](https://www.rfc-editor.org/rfc/rfc9457.html), which obsoletes RFC 7807 | The CSRF handler emits `application/problem+json` with `type`, `title`, `status` and `detail`. It departs from the RFC, which says an `about:blank` problem SHOULD use the status phrase (`Forbidden`) as its title; the SPA instead recognises the title `Invalid CSRF token`. A distinct problem type would express CSRF failures more clearly. Other denials do not need to use this body. |
| Profiles and startup checks | [Spring Boot externalized configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html) | Production disables H2 and the embedded database fallback, uses Liquibase with Hibernate validation, and externalises credentials. Validators enforce admin presence, minimum password length and threshold ordering. These are Spring mechanisms and local hardening decisions, not a named security certification. Overrides and simultaneous dev and prod profiles remain possible. |
| Session persistence and transactions | [Spring Session JDBC transactions](https://docs.spring.io/spring-session/reference/configuration/jdbc.html#customizing-how-spring-session-jdbc-uses-transactions) | Reset invokes `SessionInvalidationService` inside a transactional service, but Spring documents `REQUIRES_NEW` for JDBC session operations. No transaction override was found in the application source. Password and token changes are therefore not shown to be atomic with session deletions, and rollback behaviour needs verification. |

## Configuration reference

| Setting | Shipped value or contract | Failure or limitation |
| --- | --- | --- |
| Active profiles | Production expects prod; dev is separate | Nothing stops dev and prod from being active together. |
| DB_URL / DB_USERNAME / DB_PASSWORD | Required in prod; no defaults | Not validated; a bad value fails when the app connects. |
| APP_ADMIN_USERNAME / APP_ADMIN_PASSWORD | Blank by default in prod | Startup rejects blank values and passwords shorter than app.password-min-length (default 12). |
| APP_ADMIN_EMAIL | Defaults to admin@localhost | The seeder skips the admin if the name or email is already taken. |
| APP_CORS_ALLOWED_ORIGINS | Blank in prod | When blank, cross-origin browser requests are rejected; same-origin access still works. |
| APP_RESET_LINK_BASE_URL | Blank in prod | Not validated; email sending is still a stub. |
| app.password-reset.log-reset-link | false in prod, true by default | Keeps reset tokens out of prod logs. |
| app.ip-throttle.max-failures / app.lockout.max-failures | 4 / 5 | The app refuses to start if the throttle limit is greater than or equal to the lockout limit. |
| spring.session.timeout | 30m | Idle timeout, not a maximum session length. |
| VITE_API_BASE | Set at frontend build time; default http://localhost:8080 | Rebuild the SPA to change it. |

## Endpoint access reference

This table lists every application endpoint, plus logout and health. The dev-only H2 console is not listed. Changes (POST, PATCH and DELETE) need a CSRF token, even on public endpoints. A request that passes the filter rules can still be rejected by the handler.

| Method | Path | Boundary policy | Additional condition |
| --- | --- | --- | --- |
| POST | /api/auth/register | Public + CSRF | Body validated, per-IP budget, registration rules |
| POST | /api/auth/login | Public + CSRF | IP throttle, lock and password checks; 401 or 429 |
| GET | /api/auth/csrf | Public | Returns an XOR-masked copy of the session CSRF token in JSON |
| GET | /api/auth/me | Handler checks identity | 401 when not signed in |
| POST | /api/auth/password-reset/request | Public + CSRF | Body validated, per-IP budget, same reply for any email |
| POST | /api/auth/password-reset/confirm | Public + CSRF | Token and new-password checks |
| POST | /api/auth/logout | Logout filter + CSRF | Deletes the session; no controller involved |
| GET | /api/hello | Authenticated | Greets the signed-in user |
| GET | /api/admin/users | ROLE_ADMIN | Lists users without password hashes |
| PATCH | /api/admin/users/{id}/status | ROLE_ADMIN + CSRF | Not on your own account; disabling ends sessions |
| PATCH | /api/admin/users/{id}/role | ROLE_ADMIN + CSRF | Not on your own account; demoting ends sessions |
| DELETE | /api/admin/users/{id} | ROLE_ADMIN + CSRF | Not on your own account; removes sessions and the account |
| GET | /actuator/health | Public | The only exposed actuator endpoint |

## Technology orientation

Versions come from backend/pom.xml and frontend/package-lock.json. Versions managed by the Spring Boot bill of materials (BOM) were not resolved.

| Technology | Version evidence | Role |
| --- | --- | --- |
| Java | 21 declared target | Backend language; installed runtime not established |
| Spring Boot | 4.1.1 declared parent | MVC, external configuration and auto-configuration |
| Spring Security | BOM-managed; resolved version not verified | Filter chain, BCrypt, authentication and CSRF |
| Spring Session JDBC | BOM-managed; resolved version not verified | Server-side session persistence; configured in application.yml |
| Caffeine | BOM-managed; resolved version not verified | Process-local IP budgets in IpThrottleService |
| Liquibase / Hibernate | BOM-managed; resolved versions not verified | Schema migration and validation |
| React / Vite | 19.3.0 / 8.3.0 in the lockfile | AuthContext UI state / build-time API address |

## Glossary

| Term | Meaning |
| --- | --- |
| Authentication | Proving who you are, for example by logging in. |
| Authorisation | Deciding what you are allowed to do. |
| CSRF | Cross-site request forgery: another site tricks your browser into sending a request. A validated, session-bound token mitigates it; XSS can bypass that token. |
| CORS | Cross-origin resource sharing: the browser rule for which other origins can read API responses. It is not a login check or a firewall. |
| HttpOnly / Secure / SameSite | Cookie flags. HttpOnly: JavaScript cannot read the cookie. Secure: the cookie is sent only over HTTPS. SameSite: controls whether the cookie is sent on cross-site requests (Strict in prod). |
| Profile | A named set of Spring settings, such as dev or prod, switched on at startup. |
| Rate limiting | Capping failed attempts per IP or per account to slow down password guessing. |
| SPA | Single-page application: here, the React frontend that runs in the browser. |
| XSS | Cross-site scripting: an attacker's script runs inside your page with the user's access. |

## Further reading

Official documentation for background. Links checked on 27 September 2026.

- [Spring Security filter architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html): how filters are ordered and chains are chosen.
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html): session-stored tokens, login and logout.
- [Spring Boot externalized configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html): which property source wins.
- [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html): storing sessions in the database.
- [Spring Framework forwarded headers](https://docs.spring.io/spring-framework/reference/web/webmvc/filters.html#filters-forwarded-headers): why the proxy needs to remove client-sent forwarding headers.

## Coverage and review limits

- **Security chain, profiles and startup validators: inspected.** The current working-tree source was reviewed; the revision identifies the base HEAD.
- **Login, admin guards, password reset and browser cookie transport: partial.** Representative paths were inspected for this introduction; this is not a full audit.
- **Tests: partial.** ProdProfileTests and StartupConfigValidationTests were read but not run.
- **Infrastructure, live proxy, production database and frontend host headers: not inspected.** No deployment was exercised, so this guide makes no runtime security claims.
- **Remaining playbook topics: not applicable.** The request was for a security and configuration introduction, not a complete nine-topic playbook.

Representative source excerpts are in the [source evidence index (JSON)](artifacts/introduction/evidence.json). Nothing here was tested against a running system: no backend tests, live proxy or production database.

## Next steps

- **Before you change SecurityConfig or add a route:** reread panels 02 and 03, then update the endpoint access reference.
- **Before you deploy:** confirm what this review could not check. That includes proxy header handling and network access to the API, the active profile list, database settings, frontend host headers, and HTTPS, SameSite and CORS working together.
- **Open items in the code:** real email delivery, email addresses in logs, the reset transaction behaviour, and the bcrypt 72-byte limit.

## Maintaining this guide

The illustrated comic reader is at `artifacts/introduction/index.html`; open it in a browser without a server. Running `node artifacts/introduction/build.mjs` regenerates the reader and writes a Markdown edition to `introduction.md`. Chapter text comes from `artifacts/introduction/content.json` and the standards table from `artifacts/introduction/standards.md`. The build does not write this README, so copy edits across by hand. Diagram sources are the Archify JSON files in `artifacts/introduction/diagrams/`.
