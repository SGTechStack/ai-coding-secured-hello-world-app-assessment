# Security and deployment notes

## What is enforced

- BCrypt passwords, safe DTOs, validation, database uniqueness constraints and
  generic login/reset-request messages. Unknown usernames still perform a BCrypt
  comparison. Invalid, disabled and locked credentials share the same response.
- CSRF on every state-changing endpoint, including anonymous register/login/reset.
  `GET /api/auth/csrf` returns `{token,headerName}`; send the token with its named
  header and the session cookie. Fetch again after authentication changes.
- Exact configured CORS origin allow-list with credentials. No wildcard origins.
- `SESSION` cookie: HttpOnly, SameSite=Lax, path=/, host-only, Secure by default;
  the explicit `dev` profile disables Secure for local HTTP. Idle expiry: 30 minutes.
- Session fixation protection and server-side logout. Reset, role/status changes,
  and deletion revoke sessions; current account state is checked on each request.
- Login failure counters are serialized under a database row lock. IP protection
  has its own state and is applied before account authentication.
- Reset tokens are random, hashed in storage, short-lived and consumed under locks.
  A reset neither re-enables disabled accounts nor grants roles.
- Structured audits for success/failure, lockout, reset request/completion, and all
  admin mutations. Actor/target input is sanitized. Password-bearing request DTOs
  redact their string representation. Framework demo user generation is disabled.
- Spring Security response headers include cache controls, content-type sniffing
  protection and framing protection; the API adds a restrictive CSP and no-referrer.

## Before a real deployment

Local HTTP and the logging email stub are accepted assessment gaps, **not** a
complete deployment configuration. HTTPS is mandatory in production.

1. Terminate HTTPS at a trusted edge; retain Secure cookies, configure HSTS at that
   edge and prevent direct access to the backend. Add an appropriate CSP for the
   separately hosted SPA and configure static hosting to serve index.html for routes.
2. Configure the exact frontend origin and reset-link URL. Lax cookies work across
   same-site origins (such as these localhost ports or HTTPS subdomains). Truly
   cross-site origins require an intentional SameSite=None; Secure design and browser
   third-party-cookie testing; this build does not claim that deployment topology.
3. Replace `LoggingEmailService` with actual delivery. It intentionally logs reset
   links for assessment use; anyone with access to those logs can redeem them.
   Do not collect or publish these development logs. Do not log HTTP request bodies.
4. Supply bootstrap credentials through a secret manager/environment. There are
   no default admin credentials; first startup without valid configuration fails.
   Credentials are ignored when an ADMIN already exists. Rotate the bootstrap
   password using reset after first use and remove it from long-lived configuration.
5. Use a managed relational database with least-privilege credentials, backups and
   encrypted storage. Add its JDBC driver and vendor-specific Flyway/Spring Session
   migrations before switching URLs; H2 is the development starting point.
6. The in-memory IP limiter is for one process. It resets on restart; multiple
   instances need a shared atomic limiter. Forwarding headers are intentionally
   ignored. Behind a proxy all requests otherwise share the proxy address: configure
   trusted peer resolution at a controlled boundary, never trust arbitrary
   `X-Forwarded-For` supplied by a client.
7. Add edge request/body/concurrency limits and abuse protection for registration
   and reset email requests. The assessment's application throttle covers login;
   it is not a general DDoS or email-flood defense. Monitor and tune thresholds.
8. Pin and review dependency updates and periodically run the documented checks.
   H2 verification is not a Postgres/MySQL compatibility certification.

## JWT alternative (documented, not implemented)

A possible future design issues signed short-lived access tokens (about fifteen
minutes) and sends them using Authorization: Bearer. Keep access tokens in memory,
not localStorage. Rotate longer-lived refresh tokens, stored in an HttpOnly cookie.
True server-side logout requires a `jti` revocation store checked on authenticated
requests, with entries expiring when their tokens expire. CSRF remains relevant
to cookie-based refresh endpoints; XSS/token theft and CORS remain concerns.

JWT avoids session lookup only until revocation and refresh-token state are added.
It introduces client token handling without helping this small app, so server
sessions are the implemented choice, as required by the PRD.
