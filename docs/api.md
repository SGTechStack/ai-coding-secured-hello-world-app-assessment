# API reference

Base URL: `http://localhost:8080`. All bodies are JSON. Errors are RFC 9457 problem documents
(`application/problem+json`) with `status`, `title`, `detail`, optional `errors[]`
(`{field, message}`) and `correlationId`.

## CSRF handshake

Every `POST`/`PATCH`/`DELETE` must carry the header `X-CSRF-TOKEN` obtained from
`GET /api/auth/csrf` **for the current session**. The token rotates on login and on logout, so
fetch it again after either. A missing or stale token yields `403` with
`detail: "CSRF token missing or invalid"`.

## Public

| Method | Path | Body | Success | Errors |
| --- | --- | --- | --- | --- |
| GET | `/api/auth/csrf` | — | `200 {headerName, token}` | — |
| GET | `/api/auth/me` | — | `200 {authenticated, username, role}` | — |
| POST | `/api/auth/register` | `{username, email, password}` | `201 UserSummary` | `400` validation / password policy, `409` username or email taken |
| POST | `/api/auth/login` | `{username, password}` | `200 {authenticated:true, username, role}` + `SESSION` cookie | `401` generic (wrong password, unknown user, locked, disabled), `429` IP throttled |
| POST | `/api/auth/password-reset/request` | `{email}` | `200 {message}` (same for any email) | `400` invalid email |
| POST | `/api/auth/password-reset/confirm` | `{token, newPassword}` | `200 {message}` | `400` invalid/expired/used token or weak password |

## Authenticated (any role)

| Method | Path | Success | Errors |
| --- | --- | --- | --- |
| GET | `/api/hello` | `200 {message: "Hello, <username>"}` | `401` |
| POST | `/api/auth/logout` | `204`, cookie cleared, session deleted | `401`, `403` (CSRF) |

## Admin (`ROLE_ADMIN`)

| Method | Path | Body | Success | Errors |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/users` | — | `200 UserSummary[]` | `401`, `403` |
| PATCH | `/api/admin/users/{id}/status` | `{enabled: boolean}` | `200 UserSummary` | `400` own account, `404` |
| PATCH | `/api/admin/users/{id}/role` | `{role: "USER" \| "ADMIN"}` | `200 UserSummary` | `400` own account, `404` |
| DELETE | `/api/admin/users/{id}` | — | `204` | `400` own account, `404` |

`UserSummary` = `{id, username, email, role, enabled, createdAt}`. Password hashes are never
serialised.

Disabling, changing the role of, or deleting a user also deletes that user's server-side
sessions, so the change takes effect on their next request.

## Security headers

Every `/api/**` response carries `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`,
`Referrer-Policy: no-referrer`, `Permissions-Policy`, `X-Content-Type-Options: nosniff`,
`X-Frame-Options: DENY`, `Cache-Control: no-store` and an `X-Correlation-Id` echo.
