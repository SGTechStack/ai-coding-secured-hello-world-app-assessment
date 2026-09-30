# INFRA-BE-02: Session auth foundation is operational

`infrastructure` · wave 1

| Effort | Float |
| --- | --- |
| 1.3 days | 0.5 days |

## Acceptance criteria

- Unauthenticated calls to `/api/**` (other than the public auth endpoints) return `401` with a JSON body; authenticated non-admins hitting `/api/admin/**` get `403`.
- Session cookie is `HttpOnly`, `SameSite=Lax`, `Secure` outside the `dev` profile, and issued by Spring Session.
- Session-fixation protection applies on login (session id changes).
- CSRF protection is enabled for all state-changing endpoints; `GET /api/auth/csrf` hands the SPA its token.
- CORS allows exactly the configured frontend origin(s) with credentials.
- Security headers (CSP, Referrer-Policy, Permissions-Policy) are emitted.

## Dev tasks

1. `auth_service` (1 d) — `SecurityConfig` filter chain, `InMemoryIndexedSessionRepository` + `@EnableSpringHttpSession`, `DefaultCookieSerializer`, CSRF, CORS source, JSON `AuthenticationEntryPoint` and `AccessDeniedHandler`, `BCryptPasswordEncoder`, `HttpSessionEventPublisher`.

## Dependencies

- Blocked by: —
- Unblocks: 1, 2, 5, 7, 8
