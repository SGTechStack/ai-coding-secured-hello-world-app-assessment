Status: ready-for-agent

# Spec: Secured Hello World (React + Spring Boot)

Sources: `prd/assessment-prd.md`, `CONTEXT.md` (glossary — use its terms), ADR 0001 (App-Standards override the PRD where they conflict, and records every acknowledged deviation from the Standards), ADR 0002 (session-bound CSRF). The applicable App-Standards are the Standalone User Access Control standard and the Structured Logging standard (with its Log Schema). The spec has also been audited against the IM8 application controls (report under `artifacts/spec-compliance/`); accepted IM8 deviations are recorded in ADR 0001. Where this spec and the PRD differ, this spec wins; every such difference is recorded in ADR 0001.

## Problem Statement

A person needs a web application where they can create an Account, log in, see content that proves they are authenticated, log out, and recover access if they forget their password. An Admin needs to review and manage other Accounts. Because this is a security reference implementation, every one of those flows must resist the standard attacks — brute force, credential stuffing, account enumeration, session fixation and replay, CSRF, and privilege escalation — and must meet the organisation's Standalone User Access Control and Structured Logging standards, not just the happy path.

## Solution

A React single-page app on its own origin talks to a Spring Boot REST API on another origin. The API runs as a single instance. Authentication uses a server-side Session held in a database and carried by an HttpOnly cookie. The SPA fetches a session-bound CSRF token from a bootstrap endpoint and sends it on every state-changing request. Visitors can register, log in, and reset a forgotten password through an emailed Reset Token (email is a logging stub). Logged-in Account holders see a personalised greeting, can view their own Account, and can change their password. Admins can list Accounts, enable or disable them, change roles, unlock Locked Accounts, and delete Accounts, which leaves a tombstone. Brute force is limited by per-Account lockout, a per-Account login rate limit, and an IP Throttle. An Admin is created at startup when none exists. Every log line is structured ECS JSON with trace and correlation IDs, and security events go to a dedicated audit log.

## User Stories

### Registration

1. As a Visitor, I want to register with a username, email and password, so that I can log in to the app.
2. As a Visitor, I want to be told when my password is shorter than 12 characters, longer than 64 characters, or longer than 72 bytes, so that I can choose one that is accepted.
3. As a Visitor, I want to be told exactly which password rule I broke (length, uppercase, lowercase, digit, special character, common password), so that I can fix it in one try.
4. As a Visitor, I want any non-alphanumeric character, including spaces and `!`, to count as a special character, so that I am not rejected for arbitrary reasons.
5. As a Visitor, I want a clear error when my username is not 3–32 letters and digits, so that I can pick a valid one.
6. As a Visitor, I want a clear error when my email is malformed or longer than 254 characters, so that I can correct it.
7. As a Visitor, I want a single "user exist" error when my username or email is already taken, so that I know to choose differently. The error does not say which of the two clashed.
8. As a Visitor, I want usernames to be case-insensitive, so that nobody can register "Alice" to impersonate "alice".
9. As a Visitor, I want to be sent to the login page after registering, so that I log in explicitly with my new credentials.
10. As a Visitor whose email belonged to a Deleted Account, I want to be able to register with that email again, so that deleting an Account doesn't lock me out forever.
11. As an operator, I want a Deleted Account's username never to be registrable again, so that audit records always refer to one person.
12. As an operator, I want registration rate-limited per IP address, so that bulk probing for existing usernames and emails is slow.
13. As an operator, I want every registered Account to get the User role whatever the request contains, so that nobody can register themselves as an Admin.

### Login and Sessions

14. As an Account holder, I want to log in with my username and password, so that I can reach protected content.
15. As an Account holder, I want the same error whether I mistype my password or my username doesn't exist, so that attackers cannot discover which usernames exist.
16. As an Account holder, I want login to take the same time whether or not the username exists, so that timing doesn't reveal it either.
17. As an Account holder, I want my Session ID replaced when I log in, so that a Session ID planted before login is useless.
18. As an Account holder, I want a new login to end my previous Session, so that a forgotten logged-in browser elsewhere stops working.
19. As an Account holder, I want a failed login to end any Session the browser already carried, so that a half-trusted browser is never left authenticated after a bad attempt.
20. As an Account holder, I want my Session to end after 15 minutes of inactivity, so that an unattended browser isn't left logged in.
21. As an Account holder, I want my Session to end 8 hours after login regardless of activity, so that a stolen Session has a bounded lifetime.
22. As an Account holder, I want the SPA to take me quietly to the login page when my Session has ended, so that I never see a raw error.
23. As a Disabled Account holder, I want login to fail with the same generic error, so that my Account's state isn't disclosed to whoever is typing.
24. As an Account holder, I want to land on the hello screen after logging in, so that I always start from a known, safe page.

### Lockout, rate limiting and IP Throttle

25. As an Account holder, I want my Account Locked for 20 minutes after 5 consecutive failed passwords, so that my password cannot be brute-forced.
26. As an Account holder, I want a Locked Account to reject even my correct password until the lock ends, so that the lock actually protects me.
27. As an Account holder, I want a Locked Account to give the same generic error as a wrong password, so that attackers can't tell a lock happened.
28. As an Account holder, I want the failure counter reset when I log in successfully, so that old typos don't count against me.
29. As an Account holder, I want lockout state to survive a server restart, so that restarting doesn't give an attacker a fresh set of guesses.
30. As an Account holder, I want login attempts against my username limited to 10 per minute, so that high-speed guessing is refused with a clear "retry after" signal.
31. As an operator, I want an IP address blocked for 15 minutes after 20 failed logins across any usernames within 15 minutes, so that one attacker cannot spray passwords across many Accounts.
32. As an Account holder, I want the IP Throttle to be separate from my Account's lockout, so that an attacker can't use it to lock me out and a lock on me doesn't block other people.
33. As a client, I want every rate-limited or throttled response to be 429 with a `Retry-After` header, so that I know when to try again.
34. As an Account holder, I want to be notified when my Account becomes Locked, so that I know someone may be guessing my password.

### Logout

35. As a logged-in Account holder, I want to log out, so that my Session ends on the server and cannot be reused.
36. As a logged-in Account holder, I want logout to clear the session cookie and tell my browser to clear site data, so that nothing from my Session is left behind.
37. As a security reviewer, I want a session cookie captured before logout to be rejected afterwards, so that replaying it is useless.
38. As an Account holder, I want to land on the login page with a "you have logged out" message, so that I can see the logout worked.

### Protected content and own Account

39. As a logged-in Account holder, I want `GET /api/hello` to answer "Hello, <username>", so that I can confirm I'm authenticated.
40. As a Visitor, I want `GET /api/hello` to return 401, so that protected content is never exposed.
41. As a logged-in Account holder, I want to fetch my own Account (id, username, email, role), so that the SPA knows who I am and whether I'm an Admin.
42. As a security reviewer, I want the own-Account endpoint to use only the authenticated identity and never take an ID from the request, so that one Account holder cannot read another's record.
43. As a logged-in User, I want the admin screen hidden from me, so that the interface matches what I'm allowed to do. The API still enforces this separately.

### Password Change

44. As a logged-in Account holder, I want to change my password by entering my current password and a new one, so that I can rotate my password without a reset.
45. As a logged-in Account holder, I want Password Change to fail if my current password is wrong, so that someone who has hijacked my Session cannot take over my Account.
46. As a logged-in Account holder, I want the new password checked against the password policy and my Password History, so that I can't weaken or recycle it.
47. As an Account holder, I want every Session, including the current one, ended after a Password Change, so that anyone holding an old Session is locked out and I log in again with the new password.
48. As an Account holder, I want any pending Reset Token cancelled when I change my password, so that an old reset email can't undo my change.
49. As an Account holder, I want to be notified when my password changes, so that I can react if I didn't do it.

### Password reset

50. As an Account holder who forgot my password, I want to request a reset by entering my email, so that I can regain access without an Admin.
51. As a Visitor, I want the reset request to give the same success response, in the same time, whether or not the email is registered, so that neither the response nor its timing can be used to discover Accounts.
52. As an Account holder, I want the reset link to carry the Reset Token in the URL fragment, so that the token never reaches server logs or `Referer` headers.
53. As an Account holder, I want a Reset Token to expire after 30 minutes, so that an old email can't be used later.
54. As an Account holder, I want a Reset Token to work only once, so that a leaked, already-used link is worthless.
55. As an Account holder, I want requesting a new reset to cancel any earlier pending Reset Token, so that only the newest email works.
56. As an Account holder, I want the new password checked against the password policy and my Password History, so that a reset doesn't let me weaken or recycle it.
57. As an Account holder, I want all my Sessions ended when a reset completes, so that anyone who was logged in as me is removed.
58. As an Account holder, I want a reset to leave an active lock in place, so that a reset can't be used to get around the lockout.
59. As an operator, I want a Disabled Account to receive no Reset Token and no email while the response stays generic, so that suspended Accounts can't be recovered by self-service.
60. As an operator, I want reset requests limited per email address and per IP, and Reset Token submissions limited per IP, so that tokens can't be brute-forced or emails spammed.
61. As an Account holder, I want to be notified when a password reset completes, so that I can react if I didn't request it.
62. As an operator, I want only a hash of each Reset Token stored, so that a database leak doesn't expose usable tokens.

### Admin: account management

63. As an Admin, I want to list all Accounts with username, email, role, whether each is enabled, whether it is Locked, and when it was created, so that I can review who has access.
64. As a security reviewer, I want the Account list never to include password hashes or history, so that credential material stays inside the server.
65. As a User, I want every `/api/admin/**` request to return 403 for me, so that I cannot perform admin actions even by crafting requests.
66. As an Admin, I want to disable or re-enable another Account, so that I can suspend access without losing data.
67. As an Admin, I want disabling an Account to end its Sessions immediately, so that the suspension takes effect straight away.
68. As an Admin, I want to change another Account's role between User and Admin, so that I can grant or revoke admin rights.
69. As an Admin, I want a role change to end that Account's Sessions, so that the new rights apply from its next login.
70. As an Admin, I want to unlock a Locked Account, so that a legitimate Account holder doesn't have to wait out the lock.
71. As an Admin, I want to delete another Account, so that Accounts that should no longer exist are removed.
72. As an auditor, I want every Deleted Account kept indefinitely as a tombstone recording its username, email, deletion time and the Admin who deleted it, so that the audit trail survives deletion.
73. As an Admin, I want to be prevented from disabling, demoting, unlocking or deleting my own Account, so that I can't accidentally lock myself out, and a hijacked admin Session can't lift a lock that is protecting my Account.
74. As an operator, I want any role change, disable or delete that would leave no enabled Admin rejected, even when two Admins act at the same moment, so that the system never ends up without an Admin.
75. As an auditor, I want every admin action, including viewing the Account list, logged with the acting Admin, the target Account and the state before and after, so that changes and access to personal data can be attributed.

### Bootstrap Admin

76. As an operator deploying for the first time, I want a Bootstrap Admin created from configured credentials and email when no Account holds the Admin role, so that I can reach the admin screens without editing the database.
77. As an operator, I want the Bootstrap Admin's password stored exactly like any other password, so that it gets the same protection.
78. As an operator, I want restarts not to create another Admin while any Admin Account exists, even a Disabled one, so that no admin appears behind my back.
79. As a developer, I want the `dev` profile to fall back to `admin` / `password` / `admin@localhost` when no admin configuration is set, with a loud warning, so that local setup needs no configuration.
80. As an operator, I want every profile other than `dev` to fail at startup when the admin username, password or email is missing, so that production can never ship with a known default password.
81. As an operator, I want startup to fail with a clear message when the configured admin username is already used by an Account or a Deleted Account, so that the app never silently promotes the wrong person.

### Cross-cutting security

82. As a security reviewer, I want every state-changing request (including register, login, logout and reset) to require a CSRF token tied to the Session, so that other sites cannot forge requests.
83. As a security reviewer, I want a CSRF token issued before a login or logout rejected afterwards, so that a leaked token stops working once the Session changes.
84. As a security reviewer, I want the CSRF token served only from a bootstrap endpoint that is never cached, and never set as a cookie, so that it can't leak through cookies or caches.
85. As a security reviewer, I want CORS limited to an explicit list of frontend origins with credentials allowed and no wildcard, and the `localhost` default to apply only in `dev`, so that only the real SPA can read the CSRF token and API responses.
86. As a security reviewer, I want every API response to carry HSTS, CSP, X-Frame-Options, X-Content-Type-Options and Permissions-Policy headers, and the SPA to declare its own CSP, so that clickjacking, MIME sniffing and injected content are blunted.
87. As a security reviewer, I want session cookies to be HttpOnly and SameSite=Lax, and Secure in every profile except `dev`, so that scripts can't read them and they don't travel on cross-site requests.
88. As a security reviewer, I want oversized or malformed input rejected with 400 before any business logic runs, so that attackers can't exhaust resources or reach unexpected code paths.
89. As a client developer, I want every error returned in one stable, machine-readable format with a `code` field, so that the SPA can react without parsing text.

### Audit logging

90. As an auditor, I want structured audit events for successful and failed logins, logouts, Session expiry and forced Session ends, lockouts, unlocks, registrations, reset requests and completions, password changes, every admin action, CSRF rejections, input-validation failures, application startup and shutdown, and Bootstrap Admin creation, so that security events can be reconstructed.
91. As a privacy officer, I want logs to identify Accounts only by UUID, never by username or email, and never to contain a client IP address in clear, so that logs don't hold personal data.
92. As a security reviewer, I want passwords, Reset Tokens, Reset Token hashes, CSRF tokens and raw Session IDs never logged, so that logs can't be used to take over Accounts.
93. As an operator, I want failures, lockouts, rate-limit breaches, 403s and rejected admin actions logged at WARN, successful security events at INFO, and authentication system failures at ERROR, so that alerting can key off log level.
94. As an auditor, I want audit events written to their own log destination and kept for at least 90 days, so that an incident can be investigated weeks later.

### Structured logging

95. As an operator, I want every log line to be ECS JSON carrying the service name, version, environment, trace ID and correlation ID, so that one request can be followed across every line it produced.
96. As an operator, I want a startup event naming the host, active profiles, service version and key non-secret settings, so that I can confirm what is running without inspecting the deployment.
97. As an operator, I want every request logged at start and end with method, path, status, duration and outcome, but never the client IP, query string, headers or body, so that traffic is observable without leaking data.
98. As an operator, I want every unexpected exception logged once at ERROR with an error code, category and follow-up flag, so that alerts fire only on actionable failures.
99. As a security reviewer, I want user-supplied text neutralised before it reaches a log line, and sensitive fields masked at the logging boundary, so that log injection and accidental leaks are both blocked.

### SPA

100. As a Visitor, I want register, login, forgot-password and reset-password screens, so that I can complete every Visitor flow in the browser.
101. As a logged-in Account holder, I want a hello screen that links to Password Change and logout, so that everything I can do is one click away.
102. As an Admin, I want an admin screen listing Accounts with enable/disable, role, unlock and delete actions, so that I can manage Accounts in the browser.
103. As an Account holder, I want the reset-password screen to read the Reset Token from the link and then remove it from the address bar, so that it isn't left in browser history.
104. As an Account holder, I want the SPA to fetch a fresh CSRF token after login and logout, so that my next action isn't rejected because the token changed.
105. As a Visitor or Account holder, I want a clear "try again later" message when I'm rate-limited or throttled, so that I know it isn't a password problem.

### Required password change

106. As an operator, I want the Bootstrap Admin required to change its configured password at its first login, so that a password known to whoever deployed the app doesn't stay in use.
107. As an Admin, I want to require another Account to change its password when I suspect it is compromised, and have that Account's Sessions ended at once, so that the holder must choose a new password before doing anything else.
108. As an Account holder whose password must be changed, I want every action except viewing my Account, changing my password and logging out refused until I change it, so that a compromised or issued password can't be used for anything else.
109. As an Account holder, I want the requirement cleared once I change or reset my password, so that I can use the app normally again.
110. As an Account holder whose password must be changed, I want the SPA to take me straight to the Password Change screen after login, so that I know what to do.

### Operations and compliance

111. As an operator, I want request latency, traffic, error and saturation metrics on a management port that isn't publicly reachable, so that I can watch the app's health without exposing internals.
112. As a security researcher, I want `/.well-known/security.txt` on the SPA's origin, so that I know where to report a vulnerability.
113. As an Account holder, I want every input field labelled with its data classification ("Confidential"), so that I know how the data I type is handled.

## Implementation Decisions

### Repository and stack

- One repository with two sibling applications: `backend` and `frontend`.
- Backend: Java 21, Spring Boot 4.x, Spring Security 7.x, Maven, Spring Data JPA, Spring Session JDBC, Flyway, bucket4j (or an equivalent in-memory token bucket) for rate limits, Micrometer Tracing (no exporter needed) for trace IDs, Spring Boot Actuator with Micrometer metrics, and the OWASP Dependency-Check Maven plugin in a `security` profile (`mvn -P security verify`) that fails on critical CVEs.
- Persistence: H2 stored in a file for the `dev` profile; Postgres is the production target. Schema managed only by Flyway migrations written to also run on Postgres and MySQL; Hibernate never generates the schema. Spring Session's tables are created by a migration too, not auto-initialised. Every query uses Spring Data derived queries or bound parameters; SQL, JPQL and native queries are never built by string concatenation.
- Frontend: Vite, React, TypeScript, npm, react-router, plain CSS, no component library.
- Local dev is genuinely cross-origin: SPA on `http://localhost:3000`, API on `http://localhost:8080`, no dev proxy. All API calls send credentials.
- Audience: this is a reference implementation for the assessment, not a service deployed to real government users, so the IM8 controls for SSO, Singpass/Corppass, SCIM provisioning and WOGAA are accepted deviations (ADR 0001).
- Deployment topology: a single API instance (ADR 0001). Rate limiters and the IP Throttle are in memory and are not shared. Lockout state and Sessions are in the database and survive restarts.

### Deep modules (backend)

Each hides its policy behind a small interface. Controllers stay thin and call into these.

- **Credential policy**: validates a candidate password against length (12–64 characters, and at most 72 bytes in UTF-8, because BCrypt ignores anything longer), character classes (uppercase, lowercase, digit, special — special means any printable character that is not a letter or digit, whitespace allowed; Unicode letters and digits count as letters and digits), a bundled list of common passwords (compared case-insensitively), and the Account's Password History (last 3, including the current password). Returns every violated rule so the error can list them. Hashes with BCrypt at cost 12. Used by registration, Password Change, reset confirmation, and Bootstrap Admin creation (the `dev` fallback skips the policy checks).
- **Authentication guard**: owns the login decision. In order: IP Throttle check → per-username rate limit (10/min) → credential check → Locked/Disabled check → counter update. Returns one generic failure for unknown username, wrong password, Locked and Disabled. For an unknown username it still runs a BCrypt comparison against a fixed dummy hash, so timing matches. Increments the failure counter, locks for 20 minutes at 5 consecutive failures (stored on the Account, so it survives restarts), resets the counter on success, and triggers the lock notification. A failed login invalidates any Session the request carried.
- **IP Throttle**: an in-memory sliding count of failed logins per client address. 20 failures in 15 minutes blocks that address for 15 minutes. The client address is always the direct connection address; forwarded headers are ignored.
- **Rate limiters**: in-memory, keyed buckets. Login: 10/min per username. Registration: 10/hour per IP. Reset request: 3/hour per email and 10/hour per IP. Reset confirmation: 10/min per IP. Reset confirmation is keyed per IP only, because an invalid token identifies no Account and a 256-bit token cannot be guessed (ADR 0001). When exceeded, return 429 with `Retry-After`. Limiters expose a reset operation used only by tests.
- **Session control**: one Session per Account (a new login ends the old one; the limit is configurable, default 1); 15-minute idle timeout; 8-hour absolute timeout enforced by a filter that records the Session's start time; "end every Session for Account X" through Spring Session's lookup-by-principal. Called by Password Change, reset confirmation, and admin disable, role change, require password change and delete. Emits a `session-end` audit event when a Session expires or is ended by the system.
- **Password reset service**: issues a Reset Token (at least 32 bytes from a secure random source, URL-safe encoded), stores only its SHA-256 hash with a 30-minute expiry, and cancels any earlier pending token for the Account. Redeems a token once and records when it was used. Always returns the generic response. Issues nothing for unknown or Disabled Accounts. Leaves an active lock in place. The reset-request endpoint returns 202 before any lookup: token issuance and the email run on a background executor, so response time doesn't depend on whether the Account exists. The executor uses a `TaskDecorator` that copies the MDC (trace ID, correlation ID) into the task and clears it afterwards.
- **Required password change**: a `password_change_required` flag on the Account. It is set on the Bootstrap Admin when created, and by the admin "require password change" action. It is cleared by a successful Password Change or reset confirmation. While it is set, a filter placed after authentication returns 403 `password_change_required` for every authenticated request except `GET /me`, `PATCH /me/password`, `POST /logout` and `GET /csrf`. There is no grace-period disablement (ADR 0001). Setting and clearing the flag emit `password-change-enforcement` audit events.
- **Account administration service**: list, enable/disable, change role, unlock, require password change, delete. Requiring a password change sets the flag and ends the target's Sessions, so a suspected attacker is logged out and the holder must change the password at next login. Every service method carries `@PreAuthorize("hasRole('ADMIN')")`, in addition to the URL rule. Enforces the self-action guard (the acting Admin cannot target their own Account for disable, role change, unlock or delete) and the last-Admin rule: under a pessimistic row lock in the same transaction, reject any change that would leave zero enabled Admins. Delete writes the tombstone and removes the Account in one transaction, then ends the Account's Sessions. Viewing the Account list is audited, because it exposes every Account's email.
- **Bootstrap Admin initializer**: runs at startup. Acts only when no Account holds the Admin role, whether enabled or not. Reads the admin username, password and email from configuration. In the `dev` profile, missing values fall back to `admin` / `password` / `admin@localhost`, skipping the policy, with a WARN log. In any other profile, missing values fail startup. Startup also fails with a clear message if the username belongs to an existing Account or a tombstone. The Bootstrap Admin is created with `password_change_required` set, in every profile. Creating it emits a `user-provisioning` audit event.
- **Notifications (`EmailService` stub)**: four operations — send password-reset link, notify password changed, notify Account Locked, notify reset completed. The stub stands in for a mailbox. It writes to a dedicated logger routed to its own file, never to the application or audit log. The recipient address is masked in that file; the reset link is written in full so it can be used locally (ADR 0001). The reset link is `<frontend-origin>/reset-password#token=<token>`. Delivery is fire-and-forget, so there is no retry policy while email is a stub.
- **Audit log**: one place that emits structured security events through the SLF4J fluent API (`log.atInfo()...log()`, metadata only through `.addKeyValue()`, a short static message) to a dedicated audit logger. Its appender writes to a separate rolling JSON file that keeps at least 90 days of history. The platform must forward that file to central logging kept for at least 90 days (see Further Notes). See **Audit event contract** below for fields and levels.
- **Logging foundation**: see **Structured logging** below.
- **Clock**: every time-based decision (lockout expiry, token expiry, absolute timeout, IP Throttle windows, rate-limit refill if feasible) reads one injectable `java.time.Clock` bean in the `Asia/Singapore` zone (UTC+8), the same zone used for log timestamps.

### Audit event contract

- Fields on every audit event: `event.action` (from the Log Schema's allowed values, mapped below), `event.outcome` (`success`/`failure`), `event.reason` for failures (a generic, machine-readable reason), `user.id` (the acting Account's UUID, once resolved), `trace.id` (added by the logging foundation), `url.path` and `http.request.method`. Admin actions add the target Account's UUID as `target.user.id` and the state before and after the change (for example `enabled` false → true, `role` USER → ADMIN). Authentication events add `authentication.method: password`. Before authentication, where no UUID is known, events omit user identity and carry `session.hash` (SHA-256 of the Session ID) for correlation. IP Throttle events carry `source.ip_hash` (HMAC-SHA-256 of the client address with a configured key), never the address itself.
- `event.action` mapping:

| Event | `event.action` |
|---|---|
| Login success or failure | `user-authentication` |
| Logout | `user-logout` |
| Session expired (idle or absolute), or ended by a second login, Password Change, reset or admin action | `session-end` |
| Registration, Bootstrap Admin creation | `user-provisioning` |
| Admin list, enable/disable, role change, unlock, delete (including rejected attempts) | `user-administration` |
| Password change required: set, cleared, or a request refused because of it | `password-change-enforcement` |
| Reset request, reset completion, Password Change (the last with `event.type: ["change"]`) | `password-reset` |
| Lockout, IP Throttle block, rate-limit breach, 403, CSRF rejection, input-validation failure | `access-control` |
| Application startup and shutdown | `application-startup` / `application-shutdown` |

- Never logged: usernames, emails, passwords, Reset Tokens, Reset Token hashes, CSRF tokens, raw Session IDs, client IP addresses, request bodies.
- Levels: INFO for successes (login, logout, registration, Password Change, reset issuance and completion, admin actions). WARN for failed logins, lockouts, rate-limit and IP Throttle breaches, 403s, CSRF rejections, validation failures and rejected admin actions (`self_action_forbidden`, `last_admin`). These stay at WARN as the User Access Control standard requires, although the Logging standard would put business-rule failures at ERROR (ADR 0001). ERROR for authentication system failures, such as the database being unavailable during login.
- Validation failures are logged once per request, with the names of the failing fields only, never their values.

### Structured logging

- Output: `logging.structured.format.console=ecs` and `logging.structured.format.file=ecs`. Newline-delimited UTF-8 JSON to stdout, plus a rolling JSON application-log file as the local buffer for a forwarding agent. RFC 3339 timestamps in UTC+8. `service.name`, `service.version` and `service.environment` come from `logging.structured.ecs.service.*`. Text and JSON are never mixed in one stream.
- Tracing: Micrometer Tracing puts `trace.id` and `span.id` into MDC for every request.
- Correlation filter: a servlet filter reads `X-Correlation-ID` if a caller sends one (after sanitising it), or generates a UUID. It puts `correlation.id` into MDC, adds `user.id` once the request is authenticated, and clears all its MDC fields in a `finally` block. MDC holds only these fixed scalar fields, never Session IDs, even hashed.
- Request logging: one INFO line at request start (method, path) and one at request end (status code, duration, outcome). Never the client IP, query string, headers or body.
- Startup: once the app is ready, one INFO event with `host.name`, `host.ip`, active profiles, service metadata, and non-secret key settings (lockout threshold and duration, rate limits, Session timeouts). Never credentials, connection strings, database names or secrets. A matching shutdown event.
- Errors: a global `@RestControllerAdvice` turns every exception into a ProblemDetail. An unexpected error returns 500 with `code` `internal_error` and a generic `detail`, never an exception message, stack trace, SQL or class name (`server.error.include-message`, `-stacktrace`, `-exception` and `-binding-errors` are all `never`). It logs unexpected exceptions once, at ERROR, with `error_code`, `error_category` (`server`, `network`, `cert/auth`, `database`, `application`, `data`, `others`) and `error_follow_up_action` (true for `server`, `database` and `application`), and attaches the exception with `.setCause(e)`. A custom encoder maps the underscore keys to `error.code`, `error.category` and `error.follow_up_action`. Handled business exceptions are logged where they are handled, not again by the global handler. Exception messages and stack traces are sanitised before logging.
- Masking: the custom encoder masks, as `***MASKED***`, the value of any key matching `password`, `token`, `secret`, `csrf`, `session.id`, `email` or `username`, as a backstop behind the rules above.
- Log injection: every user-controlled string that can reach a log line (request path, correlation header) has CR, LF and other control characters escaped before logging.
- Hygiene: messages are static sentences, with dynamic values in key-value fields. No string concatenation, no `toString()` of entities, no `System.out` or `printStackTrace`, no log-and-rethrow, no swallowed exceptions. SQL logging (`spring.jpa.show-sql`, Hibernate SQL categories) is enabled only in `dev`.
- Data classification: Confidential (the app holds emails). The rules above are the required controls.

### Security configuration

- CSRF (ADR 0002): Spring Security's default session-stored CSRF repository with XOR (BREACH-resistant) token encoding. `GET /api/csrf` returns the token as JSON with no-cache headers. The client sends it in the `X-CSRF-TOKEN` header. Required on every POST/PUT/PATCH/DELETE, including register, login, logout and reset; not required on GET or HEAD. A token is reused for the life of the Session. Spring issues a new token at login and at logout, and any token issued before that change is rejected afterwards. The SPA fetches it again after login and logout. The CSRF cookie repository is prohibited.
- CORS: an allowlist of origins from configuration registered on the API base path, with no per-endpoint overrides. The default `http://localhost:3000` applies only in the `dev` profile. Every other profile fails at startup when no allowlist is configured. Credentials allowed. Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS. Headers: `Content-Type`, `X-CSRF-TOKEN`, `X-Correlation-ID`. No wildcard.
- Headers: HSTS (1 year, includeSubDomains), `Content-Security-Policy: default-src 'self'; object-src 'none'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, and a restrictive `Permissions-Policy`. `Cache-Control: no-store` on authenticated responses (Spring's default). The SPA's `index.html` declares its own CSP in a `<meta http-equiv="Content-Security-Policy">` tag (`default-src 'self'; connect-src 'self' <API origin>; object-src 'none'`), and whatever hosts the SPA must also send the header set (see Further Notes).
- Session cookie: HttpOnly, `SameSite=Lax`. `Secure=false` in the `dev` profile only (the PRD's local-HTTP carve-out, which also covers Safari rejecting Secure cookies on `http://localhost`); `true` everywhere else. TLS is required outside `dev`. It is provided by the deployment, not the app (ADR 0001). It must be TLS 1.2 or later (never SSLv3, TLS 1.0 or 1.1). Database connections also use TLS outside `dev`. Certificate and hostname validation are never disabled.
- Secrets: outside `dev`, the Bootstrap Admin password, the IP-hash HMAC key and the datasource credentials are injected from a secrets manager. They are never committed to the repository or to configuration files, and never logged. Startup fails outside `dev` when the HMAC key is missing. The HMAC key is rotated periodically through the secrets manager; rotation only breaks correlation of `source.ip_hash` across the rotation point. The `dev` profile uses fixed local values.
- Metrics: Spring Boot Actuator runs on a separate management port that must not be publicly routed. It exposes only `health` (status only, no details) and `prometheus` (request latency, traffic and errors from `http.server.requests`, plus JVM and connection-pool saturation). The API's own filter chain and public paths are unchanged.
- Logout: invalidates the Session, deletes the session cookie, and returns `Clear-Site-Data: "cache","cookies","storage"`. Logout keeps CSRF protection, so logging out on an expired Session returns 401/403, and the SPA treats that as "already logged out".
- Authorisation: `@EnableMethodSecurity` with a simple role check (`hasRole('ADMIN')`) on `/api/admin/**` in the filter chain, and again as `@PreAuthorize` on every Account administration service method. Every other API path requires authentication except the public ones listed below. Any request that matches no rule is denied. There is no config-driven RBAC matrix and no role-read API; the two roles are fixed (ADR 0001). `ADMIN` plays the part of the Standard's `USER_MANAGER` role.
- An H2-console filter chain exists only in the `dev` profile.

### API contract

Base path is configurable and defaults to `/api`. The API is unversioned.

| Endpoint | Auth | Notes |
|---|---|---|
| `GET /csrf` | public | CSRF bootstrap, no-cache |
| `POST /register` | public | JSON `{username, email, password}` → 201, role always USER (any `role` in the body is ignored); 400 `password_policy` / `validation`; 400 `user_exist` (one combined code); 429 |
| `POST /login` | public | form-encoded `username`, `password` → 200 with own-Account body; 401 `authentication_failed`; 429 |
| `POST /logout` | authenticated | 200 plus `Clear-Site-Data` |
| `GET /hello` | authenticated | `"Hello, <username>"` |
| `GET /me` | authenticated | `{id, username, email, role, passwordChangeRequired}` |
| `PATCH /me/password` | authenticated | `{currentPassword, newPassword}` → 200, all Sessions ended; 400 `current_password_invalid` / `password_policy` / `password_history` |
| `POST /password-reset/request` | public | `{email}` → always 202 with generic message, returned before any lookup; 429 |
| `POST /password-reset/confirm` | public | `{token, newPassword}` → 200; 400 `token_invalid` (covers expired, used or unknown) / `password_policy` / `password_history`; 429 |
| `GET /admin/users` | Admin | list of `{id, username, email, role, enabled, locked, createdAt}` |
| `PATCH /admin/users/{id}/enabled` | Admin | `{enabled}` → 200 |
| `PATCH /admin/users/{id}/role` | Admin | `{role: USER|ADMIN}` → 200 |
| `POST /admin/users/{id}/unlock` | Admin | 200; clears the lock and resets the failure counter |
| `POST /admin/users/{id}/require-password-change` | Admin | 200; sets `password_change_required` and ends the target's Sessions |
| `DELETE /admin/users/{id}` | Admin | 204; tombstone written |

- Admin actions on your own Account (disable, role change, unlock, delete) return 403 `self_action_forbidden`. A change that would leave no enabled Admin returns 409 `last_admin`. An unknown `{id}` returns 404. While an Account's password must be changed, every authenticated request except `GET /me`, `PATCH /me/password`, `POST /logout` and `GET /csrf` returns 403 `password_change_required`.
- Errors use Spring `ProblemDetail` (RFC 9457) with an extra `code` field. `code` stays snake_case. Where the Standard names an error, `detail` carries its exact wording: `user_exist` → "user exist", `token_invalid` → "password reset token expired or invalid", and every 429 has `code` `too_many_requests` with `detail` "too many requests". Password-rule failures add a `violations` list naming each broken rule.
- Authentication failures (unknown username, wrong password, Locked, Disabled) return one body every time, with `code` `authentication_failed`, so the reason is never revealed.
- Input limits are checked before any business logic: username 3–32 `[A-Za-z0-9]`, email valid format and at most 254 characters, password at most 64 characters and 72 UTF-8 bytes, request bodies capped at a small configured size.
- Usernames and emails are converted to lowercase before storage and comparison.

### Schema (Flyway)

- `users` (PRD name kept): `id` UUID primary key, `username` unique (lowercase), `email` unique (lowercase), `password_hash`, `role` (`USER`/`ADMIN`), `enabled`, `password_change_required` (default false), `failed_login_attempts`, `locked_until` (nullable), `created_at`.
- `password_reset_tokens`: `id`, `user_id` foreign key, `token_hash` (SHA-256, unique), `expires_at`, `used_at` (nullable). Cancelling a pending token marks it used, or equivalent; tokens are never deleted.
- `password_history`: `id`, `user_id` foreign key, `password_hash`, `created_at`; the last 3 are kept per Account, including the current one.
- `deleted_users` (tombstones, kept indefinitely): `id` (the deleted Account's UUID), `username` (unique), `email`, `deleted_at`, `deleted_by` (the acting Admin's UUID). This table is the soft-delete record the Standard requires (ADR 0001).
- The standard Spring Session JDBC tables.
- Deleting an Account removes its rows in `password_reset_tokens` and `password_history`; the tombstone is the only remaining record of it.

### SPA behaviour

- A small API client wraps `fetch`. It always sends `credentials: 'include'`, attaches the cached CSRF token to state-changing requests, and fetches the token again after login and logout.
- A global response handler treats 401, and 403 from logout, as "Session ended": it clears auth state and redirects to login without showing an error. On 429 it shows a "try again later" message.
- On load, the auth context calls `GET /me` to learn whether someone is logged in and whether they are an Admin. Admin routes are hidden from non-Admins; the API still enforces access separately.
- Screens: register, login, hello (links to Password Change, the admin screen for Admins, and logout), forgot password, reset password (reads `#token=` and then clears the fragment with `history.replaceState`), Password Change, and admin users.
- Login success always goes to the hello screen. The SPA never follows a return URL. Logout goes to the login page with a "you have logged out" message. Registration success goes to the login page. Password Change and reset success go to the login page with a message.
- `index.html` carries the CSP meta tag described under Security configuration.
- All server data is rendered as text through React's normal escaping. `dangerouslySetInnerHTML` is never used. `GET /hello` returns JSON or `text/plain`, never HTML.
- When `/me` reports `passwordChangeRequired`, or any request returns 403 `password_change_required`, the SPA shows only the Password Change screen and logout.
- The admin users screen has a "Require password change" action next to unlock.
- Every input field has a visible "Confidential" classification label next to it.
- The SPA serves `/.well-known/security.txt` (RFC 9116) with a `Contact` taken from build-time configuration (`VITE_SECURITY_CONTACT`) and an `Expires` date. The `dev` default is `mailto:security@example.invalid`; a production build fails if the contact isn't set.

### Configuration (all with defaults, all overridable)

API base path; CORS allowlist (default only in `dev`); frontend origin (used to build reset links); lockout threshold and duration; per-username login limit; IP Throttle threshold, window and block duration; IP-hash HMAC key; registration and reset rate limits; Reset Token expiry; idle and absolute Session timeouts; maximum concurrent Sessions per Account; Password History length; minimum and maximum password length; BCrypt cost; common-password list location; Bootstrap Admin username, password and email; service name, version and environment for logs; application-log and audit-log file paths and audit history (default 90 days); management port; security.txt contact (frontend build).

## Testing Decisions

- **What makes a good test here:** a test drives the backend only through its HTTP API and asserts only what a client can see — status codes, response bodies, cookies and headers — plus the recorded "emails" and log lines where a story requires them. Tests do not call services directly, inspect Spring internals, or check how something is implemented. A refactor that keeps behaviour unchanged must not break any test.
- **Seam 1 — HTTP API (primary):** full application-context integration tests using MockMvc against a throwaway in-memory H2 database, with Flyway migrations applied. Each test gets a real session cookie and a real CSRF token by calling `GET /csrf` and logging in like the SPA does.
- **Seam 2 — Clock:** tests replace the `Clock` bean with a controllable one and move time forward to cross the lockout expiry, Reset Token expiry, idle and absolute timeouts, and IP Throttle windows.
- **Seam 3 — `EmailService`:** tests replace the stub with a recording implementation, so a test can pull the Reset Token out of the "sent" link and check owner notifications. Because reset emails are sent in the background, tests wait for the recorded email with a bounded timeout.
- **Seam 4 — log output:** tests attach in-memory appenders to the application and audit loggers, parse each captured line as JSON, and assert on its fields.
- **Test fixtures:** reset rate limiters and the IP Throttle between tests; start from an empty database with only the Bootstrap Admin, whose required password change the fixture completes, except in tests of that behaviour. All test data is synthetic and deterministic (for example `testuser123` / `testuser123@test.example.com`, fixed UUIDs); never real personal data.
- **Required coverage — PRD minimum:** login success, wrong password and unknown username return identical errors, and a Locked Account is rejected; N failures lock the Account, and logging in after the lock expires resets the counter; the IP Throttle engages independently of Account lockout; a session cookie replayed after logout is rejected; a Reset Token works once, expires, and a reset ends existing Sessions; an Admin cannot disable, delete or demote their own Account; a User gets 403 on every `/admin/**` endpoint.
- **Required coverage — added by this spec (at least one test each):**
  - *Credentials and registration:* each password rule, and that violations are listed; passwords over 64 characters or over 72 bytes rejected with `password_policy`; a common password rejected; Password History rejection; the combined `user_exist` code with `detail` "user exist"; tombstoned usernames blocked while emails are allowed; a `role` in the registration body ignored; username format and length, email format and length, and oversized request bodies rejected with 400.
  - *Login and Sessions:* unknown username, wrong password, Locked and Disabled all return the identical `authentication_failed` body; a failed login ends a Session the request carried; a second login ends the first Session; idle and absolute timeouts; lockout state and Sessions survive a restart (a second application context on the same file database).
  - *Password Change and reset:* Password Change needs the correct current password, returns 200, ends all Sessions and cancels pending Reset Tokens; a new Reset Token cancels the older one; `token_invalid` has `detail` "password reset token expired or invalid"; a reset leaves a lock in place; a Disabled Account gets no token and no email, with an identical response; the reset request returns 202 before the email is recorded.
  - *Rate limits:* 429 plus `Retry-After`, `code` `too_many_requests` and `detail` "too many requests" for each limiter.
  - *CSRF and CORS:* a missing or invalid CSRF token on a POST returns 403; a GET succeeds without one; a token fetched before login is rejected after login, and one fetched before logout is rejected after logout; `/csrf` is not cached and sets no CSRF cookie; CORS allows the listed origin and rejects others.
  - *Headers and cookies:* security headers present; the session cookie is HttpOnly and SameSite=Lax, and Secure outside `dev`; `Clear-Site-Data` and 200 on logout.
  - *Admin:* unlock clears the lock; an Admin unlocking their own Account gets 403 `self_action_forbidden`; the last-Admin rule, including two concurrent demotions; delete writes a tombstone and ends the target's Sessions; disable and role change end the target's Sessions; `/me` returns only the caller.
  - *Required password change:* the Bootstrap Admin's first login gets 403 `password_change_required` on `/hello` and `/admin/users` but can use `/me`, `/me/password`, `/logout` and `/csrf`; a Password Change clears it; an Admin requiring a password change on another Account ends that Account's Sessions and blocks it the same way until it changes or resets its password; a User calling the action gets 403.
  - *Errors and exposure:* an unexpected exception returns 500 `internal_error` with no exception message, class name or stack trace in the body; the management endpoints are not served on the API port, and `health` returns no details.
  - *Startup:* the Bootstrap Admin is created once with its configured email and `password_change_required` set, and is not re-created on restart; outside `dev`, startup fails when the IP-hash HMAC key is missing; startup fails when the configured username is taken or tombstoned; outside `dev`, startup fails when the admin username, password or email is missing, or when no CORS allowlist is configured.
  - *Audit and logging:* every event in story 90 is logged at the required level with its `event.action`, `event.outcome` and `trace.id`; audit events appear in the audit destination; no log line contains a password, token, token hash, CSRF token, Session ID, username, email or client IP address; every line parses as JSON with the required service fields; the startup event is present; an unexpected exception is logged once at ERROR with `error.category`; a request path containing CR/LF cannot forge a log line; a value under a masked key is written as `***MASKED***`.
- **Bootstrap tests** run separate application contexts with different profiles and properties, and assert either successful startup or a startup failure with the expected message.
- **Dependency scan:** `mvn -P security verify` runs OWASP Dependency-Check and fails on critical CVEs. It is run before handoff; wiring it into CI is out of scope.
- **Frontend:** no automated tests are required (PRD). A manual smoke test of every screen against the running backend, cross-origin, is expected before handoff. It includes the forced Password Change flow, the classification labels and `/.well-known/security.txt`.
- **Prior art:** none in the repo, which has no code yet. The test cases follow the "Test & Validation Standard" sections of App-Standards' Standalone User Access Control and Structured Logging standards.

## Out of Scope

- JWT authentication (the PRD appendix documents it only).
- MFA / 2FA, including for Admins (IM8 ac-2; ADR 0001).
- SSO (WOG AAD), Singpass/Corppass step-up, SCIM or JIT provisioning, and WOGAA: this is a reference implementation, not a service for real government users (IM8 ac-7, ac-8, ac-12, lm-18; ADR 0001).
- Remember Me (persistent login across browser restarts).
- Real email delivery (only the logging `EmailService` stub), and therefore notification retry.
- Containers, CI/CD (including a CI gate for OWASP Dependency-Check), hosting, and local HTTPS.
- Running more than one API instance: the rate limiters and IP Throttle would need a shared store (ADR 0001).
- Configuring the central log platform (forwarding, 90-day retention, write-once storage, access control, alerting when audit logging fails). The README states these as deployment requirements.
- Admin-initiated password reset; grace-period disablement for an unmet required password change; setting the required-change flag automatically on re-enable (ADR 0001).
- Admin creation of Accounts; Accounts come from self-registration instead (ADR 0001).
- A config-driven RBAC matrix, a role-read API, or any authorisation beyond the User/Admin role check (ADR 0001).
- Scheduled account-hygiene jobs (inactivity disablement, role revocation) and a declared per-Account permission baseline with periodic access review (IM8 ac-3, ac-4; ADR 0001). The Admin list supports a manual review.
- Email verification on registration. Registration therefore still reveals that *some* matching Account exists (ADR 0001).
- Handling of forwarded headers for proxied deployments, beyond a documentation note.
- API versioning.
- Automated frontend tests.

## Further Notes

- **Reset Token in the email stub:** the PRD says the `EmailService` stub "logs the link", and that link contains the plaintext Reset Token, which the Standards forbid logging. The resolution (ADR 0001): the stub writes to its own logger and file, which stand in for a mailbox, with the recipient masked. The application log, the audit log and every other log line must never contain the token. Revisit this if a real email integration is added.
- **Why the password maximum is 64 characters:** BCrypt uses only the first 72 bytes of its input, and recent Spring Security versions reject longer input with an exception. Capping at 64 characters and 72 bytes keeps every accepted password fully hashed, and turns overlong input into an ordinary `password_policy` error instead of a server error.
- **Deployment notes for the README:**
  - TLS 1.2 or later is required outside `dev`, including to the database; `Secure` cookies and HSTS depend on it.
  - Inject every secret (Bootstrap Admin password, IP-hash HMAC key, datasource credentials) from a secrets manager, and rotate the HMAC key periodically.
  - Never route the Actuator management port publicly.
  - Set `VITE_SECURITY_CONTACT` for production builds of the SPA.
  - Behind a reverse proxy, set `server.forward-headers-strategy` and trust only that proxy, or the IP Throttle will see every request as coming from the proxy. Behind a corporate NAT, the IP Throttle may block a whole office; raise its threshold or disable it there.
  - The app must run as a single instance.
  - The Bootstrap Admin must change its password at first login. The `dev` fallback `admin` / `password` must never be used outside local development.
  - Forward the audit-log file to central logging with at least 90 days' retention, write-once storage, restricted access, and an alert when audit events stop arriving.
  - Whatever hosts the SPA must send HSTS, CSP, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` and `Permissions-Policy` on its own responses.
  - Set the production CORS allowlist and the IP-hash HMAC key.
- **Where the rate-limit numbers come from:** lockout (5 / 20 min) and the per-username limit (10/min) are App-Standards defaults. The IP Throttle, registration and reset numbers were chosen during design review, because neither the PRD nor the Standards specify them.
- Use the `CONTEXT.md` terms in code, tests and UI copy: Account (not "user" for the record), Locked vs Disabled vs Deleted Account, Reset Token, Password Change, IP Throttle, Bootstrap Admin.
