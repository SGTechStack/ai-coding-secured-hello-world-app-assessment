# Secured Hello World implementation design

The binding specification is `prd/assessment-prd.md`. Deliver all twelve stories on
`jingshun`, using conventional commits, then push that branch for review.

## Architecture and alternatives

Use Java 21, Spring Boot, Spring Security, Spring Data JPA, JDBC Spring Session,
and H2 for development. React with TypeScript and Vite runs on localhost:3000;
the backend runs on localhost:8080. JDBC sessions share the application database
and support revocation by principal. Redis would add unnecessary infrastructure;
JWT is documented only, as the PRD requests.

Keep controllers, validation, business services, persistence, and security policy
separate. Return explicit response DTOs; never serialize entities or credentials.
Use Flyway migrations and UTC timestamps. Production uses secure cookies, exact
CORS origins, HTTPS at a trusted edge, and external bootstrap credentials.

## Security choices

- BCrypt with strength 12; passwords require 12 characters and at most 72 UTF-8
  bytes, rejecting rather than silently truncating BCrypt input.
- Normalize usernames and emails to lowercase. Unique database constraints back
  application checks. Registration always grants USER.
- Five account failures in fifteen minutes lock the account for fifteen minutes.
  A separate IP bucket blocks after three failures in fifteen minutes, before
  further account failures accrue. Thus one source cannot alone reach the account
  lock threshold. Use the direct peer IP, never untrusted forwarding headers.
  The bounded in-process limiter is for this single-instance assessment; document
  the shared-store requirement for horizontal deployment.
- Session fixation protection, explicit security-context persistence, principal
  indexing, logout invalidation, and reset/administrative session revocation.
- CSRF token fetched from GET /api/auth/csrf, held in memory, sent as X-CSRF-TOKEN
  on every mutation including anonymous login/register/reset. Refresh after login
  and logout. Cookies are HttpOnly, SameSite=Lax, Secure by default (dev override).
- Reset tokens contain 256 random bits; only SHA-256 hashes persist, expire after
  twenty minutes, and are consumed atomically. Password reset revokes all sessions
  and outstanding tokens. The development EmailService logs the reset link, never
  passwords; this sensitive development stub must be replaced for deployment.
- Check current account status/role on authenticated requests to reject stale
  privileges. Audit structured actor/target/outcome events without credentials.
- Bootstrap requires configured username/email/password only when no admin exists.
  Never ship usable default administrator credentials.

## API contract

All request/response bodies are JSON except 204 responses. Errors are
`{ "message": "..." }`, optionally with validation `errors` by field.

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| GET | /api/auth/csrf | none | {token, headerName} |
| POST | /api/auth/register | {username,email,password} | 201 User |
| POST | /api/auth/login | {username,password} | User |
| GET | /api/auth/me | none | User or 401 |
| POST | /api/auth/logout | none | 204 |
| GET | /api/hello | none | JSON string "Hello, username" |
| POST | /api/auth/password-reset/request | {email} | generic {message} |
| POST | /api/auth/password-reset/confirm | {token,password} | {message} |
| GET | /api/admin/users | none | User[] |
| PATCH | /api/admin/users/{id}/status | {enabled} | User |
| PATCH | /api/admin/users/{id}/role | {role: USER or ADMIN} | User |
| DELETE | /api/admin/users/{id} | none | 204 |

User is `{id,username,email,role,enabled,createdAt}`. Admin self mutations are all
rejected. Invalid credentials always produce the same 401 message, including
unknown, disabled, and locked users. Throttling produces 429 with Retry-After.

## UI and verification

Accessible responsive screens for login, registration, reset request/confirmation,
greeting, and admin user management. Display actionable API errors, loading states,
session expiry, and destructive-action confirmation. No persistent credential storage.

Real HTTP integration tests with H2 and JDBC sessions prove all required paths,
CSRF/CORS/cookie flags, password storage and validation, reset concurrency, and stale
session revocation. UI tests exercise forms and API credentials/CSRF behavior.
Run production frontend build and backend verify, then browser smoke test both origins.
